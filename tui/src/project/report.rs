//! The JSON that LVM's `lvmReport` task writes for each module, from LVM 0.4.0 on.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// The newest report format lvm-tui knows. A newer one is read as far as its fields are known.
pub const SCHEMA: u32 = 1;

/// Where a module's reports are, relative to the root of the build.
pub const REPORT_DIRECTORY: &str = "build/lvm/report";

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModuleReport {
    pub schema: u32,
    pub lvm_version: String,
    pub path: String,
    pub project_dir: String,
    #[serde(default)]
    pub plugins: Vec<String>,
    #[serde(default)]
    pub targets: Targets,
    #[serde(default)]
    pub toolchain: BTreeMap<String, ToolVersions>,
    #[serde(default)]
    pub values: BTreeMap<String, ReportedValue>,
    #[serde(default)]
    pub settings: Option<SettingsReport>,
    #[serde(default)]
    pub publication: Option<Publication>,
    #[serde(default)]
    pub source_sets: Vec<String>,
    #[serde(default)]
    pub project_dependencies: BTreeMap<String, Vec<String>>,
    #[serde(default)]
    pub lvm_dependencies: BTreeMap<String, Vec<String>>,
    #[serde(default)]
    pub reports: Option<CompilerReports>,
    #[serde(default)]
    pub tasks: Vec<String>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct Targets {
    pub android: Option<AndroidTarget>,
    pub ios: Option<IosTarget>,
    pub jvm: Option<BTreeMap<String, Value>>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AndroidTarget {
    pub host_tests: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct IosTarget {
    pub architectures: Vec<String>,
    pub framework: Option<Framework>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Framework {
    pub base_name: Option<String>,
    pub is_static: bool,
    pub bundle_id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ToolVersions {
    pub actual: String,
    pub minimum: String,
    pub tested: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ReportedValue {
    pub value: Value,
    pub source: Source,
}

/// Where a value comes from, weakest first.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Source {
    Default,
    /// The `lvm { }` block of the settings script.
    Settings,
    /// A `lvm.*` Gradle property, from `gradle.properties` or `-P`.
    Property,
    /// The module's own `lvm { }` block.
    Module,
}

impl Source {
    pub fn label(self) -> &'static str {
        match self {
            Self::Default => "default",
            Self::Settings => "settings",
            Self::Property => "property",
            Self::Module => "module",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsReport {
    pub catalog: bool,
    pub catalog_name: String,
    pub catalog_version: String,
    pub repositories: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Publication {
    pub group: String,
    pub artifact_id: String,
    pub version: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompilerReports {
    pub compose_metrics: Option<String>,
    pub compose_reports: Option<String>,
}

/// The reports of a build, as `lvmReport` last wrote them.
#[derive(Debug, Clone, Default, Serialize)]
pub struct Reports {
    pub modules: BTreeMap<String, ModuleReport>,
    /// When the oldest report was written, which is when the set as a whole was last complete.
    #[serde(skip)]
    pub written: Option<SystemTime>,
    /// Files that could not be read, with the reason.
    pub errors: Vec<String>,
    /// The newest schema among the reports, when it is newer than [SCHEMA].
    pub newer_schema: Option<u32>,
}

impl Reports {
    pub fn load(root: &Path) -> Self {
        let mut reports = Self::default();
        let mut files = Vec::new();
        collect_files(&root.join(REPORT_DIRECTORY), &mut files);
        for file in files {
            match read(&file) {
                Ok(report) => {
                    if report.schema > SCHEMA {
                        reports.newer_schema = reports.newer_schema.max(Some(report.schema));
                    }
                    let modified = fs::metadata(&file).and_then(|m| m.modified()).ok();
                    reports.written = match (reports.written, modified) {
                        (Some(a), Some(b)) => Some(a.min(b)),
                        (a, b) => a.or(b),
                    };
                    reports.modules.insert(report.path.clone(), report);
                }
                Err(error) => reports.errors.push(format!("{}: {error}", file.display())),
            }
        }
        reports
    }

    pub fn is_empty(&self) -> bool {
        self.modules.is_empty()
    }
}

pub fn read(file: &Path) -> anyhow::Result<ModuleReport> {
    Ok(serde_json::from_str(&fs::read_to_string(file)?)?)
}

fn collect_files(dir: &Path, files: &mut Vec<PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for entry in entries.filter_map(Result::ok) {
        let path = entry.path();
        if path.is_dir() {
            collect_files(&path, files);
        } else if path.file_name().is_some_and(|name| name == "project.json") {
            files.push(path);
        }
    }
    files.sort();
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The report that blueprint's own tests compare `lvmReport` with.
    const EXAMPLE: &str = include_str!("../../../blueprint/multiplatform/src/test/resources/lvm-report.json");

    #[test]
    fn reads_the_report_that_blueprint_writes() {
        let report: ModuleReport = serde_json::from_str(EXAMPLE).unwrap();
        assert_eq!(report.schema, SCHEMA);
        assert_eq!(report.path, ":lib");
        assert_eq!(report.plugins, ["tech.annexflow.lvm.multiplatform"]);
        let ios = report.targets.ios.as_ref().unwrap();
        assert_eq!(ios.framework.as_ref().unwrap().base_name.as_deref(), Some("Lib"));
        assert!(report.targets.jvm.is_some());
        assert!(report.targets.android.is_none());
        assert_eq!(report.values["jvm.target"], ReportedValue { value: 17.into(), source: Source::Settings });
        assert_eq!(report.values["kotlin.explicitApi"].source, Source::Property);
        assert_eq!(report.values["kotlin.optIns"].source, Source::Module);
        assert_eq!(report.project_dependencies["commonMainImplementation"], [":core"]);
        assert_eq!(report.lvm_dependencies["commonMainImplementation"], ["lvm-core"]);
        assert!(!report.settings.as_ref().unwrap().catalog);
        assert!(report.toolchain.contains_key("kotlin"));
    }

    #[test]
    fn loads_every_module_and_keeps_errors() {
        let dir = tempfile::tempdir().unwrap();
        let reports = dir.path().join(REPORT_DIRECTORY);
        fs::create_dir_all(reports.join("lib")).unwrap();
        fs::create_dir_all(reports.join("broken")).unwrap();
        fs::write(reports.join("lib/project.json"), EXAMPLE).unwrap();
        fs::write(reports.join("broken/project.json"), "{").unwrap();
        let newer = EXAMPLE.replacen("\"schema\": 1", "\"schema\": 2", 1).replace("\":lib\"", "\":next\"");
        fs::create_dir_all(reports.join("next")).unwrap();
        fs::write(reports.join("next/project.json"), newer).unwrap();

        let loaded = Reports::load(dir.path());

        assert_eq!(loaded.modules.keys().collect::<Vec<_>>(), [":lib", ":next"]);
        assert_eq!(loaded.errors.len(), 1);
        assert_eq!(loaded.newer_schema, Some(2));
        assert!(loaded.written.is_some());
    }
}
