//! `lvm-tui check`: the doctor's findings without the interface, for scripts and CI.

use std::io::Write;

use anyhow::Result;
use serde::Serialize;

use crate::doctor::{self, Environment, Finding};
use crate::edit::ChangeSet;
use crate::project::Project;

#[derive(Debug, Clone, Copy, Default)]
pub struct Options {
    pub json: bool,
    /// Applies every fix, then checks again.
    pub fix: bool,
}

#[derive(Serialize)]
struct JsonOutput<'a> {
    findings: &'a [Finding],
    /// What `--fix` changed, when it ran.
    #[serde(skip_serializing_if = "Option::is_none")]
    fixed: Option<&'a ChangeSet>,
}

/// Checks [project] and writes the findings to [out]. Returns whether errors remain, which makes the exit code 1.
pub fn run(project: Project, env: &Environment, options: Options, out: &mut impl Write) -> Result<bool> {
    let mut findings = doctor::diagnose(&project, env);
    let mut fixed = None;
    let mut project = project;
    if options.fix && findings.iter().any(|f| f.fix.is_some()) {
        let mut changes = ChangeSet::default();
        for fix in findings.iter().filter_map(|f| f.fix.as_ref()) {
            if let Err(error) = fix.apply(&project, &mut changes) {
                writeln!(out, "Could not fix: {error:#}")?;
            }
        }
        if !options.json {
            write!(out, "{}", changes.preview())?;
        }
        changes.apply(&project.root)?;
        project = Project::load(&project.root);
        let mut env = env.clone();
        env.recheck_git(&project.root);
        findings = doctor::diagnose(&project, &env);
        fixed = Some(changes);
    }

    if options.json {
        let output = JsonOutput { findings: &findings, fixed: fixed.as_ref() };
        writeln!(out, "{}", serde_json::to_string_pretty(&output)?)?;
    } else {
        write_text(&findings, fixed.is_some(), out)?;
    }
    Ok(doctor::has_errors(&findings))
}

fn write_text(findings: &[Finding], fixed: bool, out: &mut impl Write) -> Result<()> {
    if fixed {
        writeln!(out)?;
    }
    for finding in findings {
        let place = match (&finding.file, finding.line) {
            (Some(file), Some(line)) => format!("{}:{line}", file.display()),
            (Some(file), None) => file.display().to_string(),
            (None, _) => finding.module.clone().unwrap_or_default(),
        };
        let place = if place.is_empty() { String::new() } else { format!("{place}  ") };
        writeln!(out, "{:<8} {place}[{}]", finding.severity.label(), finding.rule)?;
        writeln!(out, "         {}", finding.message)?;
        if let Some(fix) = &finding.fix {
            writeln!(out, "         fix: {}", fix.label())?;
        }
    }
    let count = |severity| findings.iter().filter(|f| f.severity == severity).count();
    let fixable = findings.iter().filter(|f| f.fix.is_some()).count();
    let mut summary = format!(
        "{} error(s), {} warning(s), {} note(s)",
        count(doctor::Severity::Error),
        count(doctor::Severity::Warning),
        count(doctor::Severity::Info)
    );
    if fixable > 0 && !fixed {
        summary.push_str(&format!("; --fix fixes {fixable}"));
    }
    writeln!(out, "{summary}")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::Path;

    fn build() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        fs::write(root.join("settings.gradle.kts"), "include(\":lib\")\n").unwrap();
        fs::write(root.join("gradle.properties"), "lvm.kotlin.flatLayot=true\n").unwrap();
        fs::create_dir_all(root.join("lib")).unwrap();
        fs::write(
            root.join("lib/build.gradle.kts"),
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { jvm() } }\n",
        )
        .unwrap();
        fs::write(root.join("build.gradle.kts"), "plugins {\n    id(\"tech.annexflow.lvm.multiplatform\") apply false\n    id(\"org.jetbrains.kotlin.multiplatform\") apply false\n}\n").unwrap();
        dir
    }

    fn env() -> Environment {
        Environment { os: "linux".into(), java: Some(21), ..Environment::default() }
    }

    #[test]
    fn prints_findings_with_their_fixes_and_passes_without_errors() {
        let dir = build();
        let mut out = Vec::new();
        let errors = run(Project::load(dir.path()), &env(), Options::default(), &mut out).unwrap();
        let text = String::from_utf8(out).unwrap();
        assert!(!errors);
        assert!(text.contains("warning  gradle.properties:1  [unknown-key]"), "{text}");
        assert!(text.contains("fix: rename it to lvm.kotlin.flatLayout"), "{text}");
        assert!(text.ends_with("0 error(s), 1 warning(s), 0 note(s); --fix fixes 1\n"), "{text}");
    }

    #[test]
    fn fix_applies_every_fix_and_checks_again() {
        let dir = build();
        let mut out = Vec::new();
        run(Project::load(dir.path()), &env(), Options { json: true, fix: true }, &mut out).unwrap();
        let output: serde_json::Value = serde_json::from_slice(&out).unwrap();
        assert_eq!(output["findings"], serde_json::json!([]));
        assert_eq!(fs::read_to_string(dir.path().join("gradle.properties")).unwrap(), "lvm.kotlin.flatLayout=true\n");
        assert!(Path::new(&dir.path().join("lib/build.gradle.kts")).is_file());
    }
}
