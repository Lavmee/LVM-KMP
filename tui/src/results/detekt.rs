//! detekt's findings, from the checkstyle XML or the SARIF it writes to `build/reports/detekt/`.

use std::fs;
use std::path::{Path, PathBuf};

use quick_xml::Reader;
use quick_xml::events::{BytesStart, Event};
use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Issue {
    pub module: String,
    /// The rule without detekt's prefix, such as `MagicNumber`.
    pub rule: String,
    pub severity: String,
    pub message: String,
    pub file: PathBuf,
    pub line: usize,
}

/// The issues of every report in `build/reports/detekt` of [module_dir], by rule, then file and line.
pub fn load(module: &str, module_dir: &Path) -> Vec<Issue> {
    let dir = module_dir.join("build/reports/detekt");
    let mut files: Vec<PathBuf> =
        fs::read_dir(&dir).into_iter().flatten().filter_map(Result::ok).map(|e| e.path()).collect();
    files.sort();
    let mut issues = Vec::new();
    // Each detekt task writes the same issues in several formats; read one format of each.
    let mut seen = std::collections::BTreeSet::new();
    for file in files.iter().filter(|f| f.extension().is_some_and(|e| e == "xml")) {
        seen.insert(file.file_stem().map(|s| s.to_owned()));
        if let Ok(text) = fs::read_to_string(file) {
            issues.extend(parse_checkstyle(module, &text));
        }
    }
    for file in files.iter().filter(|f| f.extension().is_some_and(|e| e == "sarif")) {
        if seen.contains(&file.file_stem().map(|s| s.to_owned())) {
            continue;
        }
        if let Ok(text) = fs::read_to_string(file) {
            issues.extend(parse_sarif(module, &text));
        }
    }
    issues.sort_by(|a, b| (&a.rule, &a.file, a.line).cmp(&(&b.rule, &b.file, b.line)));
    issues.dedup();
    issues
}

pub fn parse_checkstyle(module: &str, xml: &str) -> Vec<Issue> {
    let mut reader = Reader::from_str(xml);
    let mut issues = Vec::new();
    let mut file = PathBuf::new();
    loop {
        match reader.read_event() {
            Ok(Event::Start(element)) | Ok(Event::Empty(element)) => match element.local_name().as_ref() {
                "file" => file = PathBuf::from(attribute(&element, "name").unwrap_or_default()),
                "error" => issues.push(Issue {
                    module: module.to_owned(),
                    rule: short_rule(&attribute(&element, "source").unwrap_or_default()),
                    severity: attribute(&element, "severity").unwrap_or_default(),
                    message: attribute(&element, "message").unwrap_or_default(),
                    file: file.clone(),
                    line: attribute(&element, "line").and_then(|l| l.parse().ok()).unwrap_or(0),
                }),
                _ => {}
            },
            Ok(Event::Eof) | Err(_) => break,
            Ok(_) => {}
        }
    }
    issues
}

pub fn parse_sarif(module: &str, json: &str) -> Vec<Issue> {
    let Ok(sarif) = serde_json::from_str::<Value>(json) else { return Vec::new() };
    let results =
        sarif["runs"].as_array().into_iter().flatten().flat_map(|run| run["results"].as_array().into_iter().flatten());
    results
        .map(|result| {
            let location = &result["locations"][0]["physicalLocation"];
            let uri = location["artifactLocation"]["uri"].as_str().unwrap_or_default();
            Issue {
                module: module.to_owned(),
                rule: short_rule(result["ruleId"].as_str().unwrap_or_default()),
                severity: result["level"].as_str().unwrap_or("warning").to_owned(),
                message: result["message"]["text"].as_str().unwrap_or_default().to_owned(),
                file: PathBuf::from(uri.trim_start_matches("file://")),
                line: location["region"]["startLine"].as_u64().unwrap_or(0) as usize,
            }
        })
        .collect()
}

/// `detekt.style.MagicNumber` and `detekt.MagicNumber` -> `MagicNumber`.
fn short_rule(source: &str) -> String {
    source.rsplit('.').next().unwrap_or(source).to_owned()
}

fn attribute(element: &BytesStart, name: &str) -> Option<String> {
    element.attributes().filter_map(Result::ok).find(|attribute| attribute.key.local_name().as_ref() == name).and_then(
        |attribute| attribute.normalized_value(quick_xml::XmlVersion::Implicit1_0).ok().map(|value| value.into_owned()),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_checkstyle_and_sarif_reports() {
        let xml = r#"<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="4.3">
<file name="/work/shared/src/Greeting.kt">
	<error line="12" column="5" severity="warning" message="This expression contains a magic number. Consider defining it to a well named constant." source="detekt.MagicNumber" />
</file>
</checkstyle>"#;
        let issues = parse_checkstyle(":shared", xml);
        assert_eq!(issues.len(), 1);
        assert_eq!(issues[0].rule, "MagicNumber");
        assert_eq!(issues[0].file, PathBuf::from("/work/shared/src/Greeting.kt"));
        assert_eq!(issues[0].line, 12);

        let sarif = r#"{"runs": [{"results": [{"ruleId": "detekt.style.WildcardImport", "level": "warning",
            "message": {"text": "Wildcard import"},
            "locations": [{"physicalLocation": {"artifactLocation": {"uri": "file:///work/shared/src/A.kt"}, "region": {"startLine": 3}}}]}]}]}"#;
        let issues = parse_sarif(":shared", sarif);
        assert_eq!(issues[0].rule, "WildcardImport");
        assert_eq!(issues[0].file, PathBuf::from("/work/shared/src/A.kt"));
        assert_eq!(issues[0].line, 3);
    }

    #[test]
    fn reads_one_format_of_each_report() {
        let dir = tempfile::tempdir().unwrap();
        let reports = dir.path().join("build/reports/detekt");
        fs::create_dir_all(&reports).unwrap();
        let xml = r#"<checkstyle><file name="/a/B.kt"><error line="1" severity="warning" message="m" source="detekt.R"/></file></checkstyle>"#;
        let sarif = r#"{"runs":[{"results":[{"ruleId":"R","message":{"text":"m"},"locations":[{"physicalLocation":{"artifactLocation":{"uri":"/a/B.kt"},"region":{"startLine":1}}}]}]}]}"#;
        fs::write(reports.join("detekt.xml"), xml).unwrap();
        fs::write(reports.join("detekt.sarif"), sarif).unwrap();
        assert_eq!(load(":m", dir.path()).len(), 1);
    }
}
