//! One view of a build, from its files and, where LVM wrote them, from its `lvmReport` reports.
//!
//! The reports are exact: they hold what LVM's plugins did. The files are read without running Gradle, so values
//! that a script computes, or that build logic sets, are missing from them. Every module and value says which of the
//! two it comes from.

use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use serde::Serialize;
use serde_json::Value;

use super::catalog;
use super::keys::{self, Key, ListMode};
use super::module::{Layout, ModuleScript, apply_writes};
use super::properties::Properties;
use super::report::{ModuleReport, Publication, Reports, Source, ToolVersions};
use super::settings::{SettingsScript, settings_file};
use super::wrapper;
use crate::version;

/// The first LVM release with `lvmReport`.
pub const REPORT_SINCE: &str = "0.4.0";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Origin {
    Report,
    Files,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Project {
    pub root: PathBuf,
    pub name: String,
    pub settings_file: Option<PathBuf>,
    pub lvm: LvmVersion,
    pub options: Options,
    pub toolchain: Vec<Tool>,
    pub modules: Vec<Module>,
    pub report: ReportState,
    #[serde(skip)]
    pub reports: Reports,
    #[serde(skip)]
    pub settings: Option<SettingsScript>,
    #[serde(skip)]
    pub properties: Properties,
    /// The root project's build script.
    #[serde(skip)]
    pub root_script: Option<ModuleScript>,
}

/// Which LVM the build uses.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "kind", content = "version", rename_all = "camelCase")]
pub enum LvmVersion {
    /// The version that the settings script pins.
    Pinned(String),
    /// The plugins come from an included build, as in LVM's own repository.
    FromSource,
    /// The settings script applies the settings plugin without a version that lvm-tui can see.
    Unpinned,
    /// No settings plugin.
    None,
}

impl LvmVersion {
    pub fn label(&self) -> String {
        match self {
            Self::Pinned(version) => version.clone(),
            Self::FromSource => "from source".to_owned(),
            Self::Unpinned => "not pinned".to_owned(),
            Self::None => "no settings plugin".to_owned(),
        }
    }
}

/// The settings plugin's own options, where they come from.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Options {
    pub catalog: Option<bool>,
    pub catalog_name: String,
    pub catalog_version: Option<String>,
    pub repositories: Option<bool>,
    pub origin: Origin,
}

#[derive(Debug, Clone, Serialize)]
pub struct Tool {
    pub name: String,
    pub actual: Option<String>,
    pub minimum: Option<String>,
    pub tested: Option<String>,
    pub origin: Origin,
}

impl Tool {
    pub fn support(&self) -> version::Support {
        version::support(self.actual.as_deref(), self.minimum.as_deref(), self.tested.as_deref())
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "camelCase")]
pub enum ReportState {
    /// Every module with an LVM plugin has a report newer than the build's scripts.
    Fresh,
    /// A script changed after the reports were written, or a module has none.
    Stale,
    /// No reports yet; `lvmReport` writes them.
    Missing,
    /// The build's LVM is older than the first release with `lvmReport`.
    Unsupported { version: String },
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Module {
    pub path: String,
    /// The module's directory, relative to the root.
    pub dir: String,
    pub build_file: Option<PathBuf>,
    pub plugins: Vec<String>,
    pub targets: Vec<String>,
    pub framework: Option<String>,
    pub layout: Layout,
    pub values: Vec<ValueRow>,
    pub project_dependencies: Vec<Edge>,
    pub lvm_dependencies: Vec<Edge>,
    pub source_sets: Vec<String>,
    pub tasks: Vec<String>,
    pub publication: Option<Publication>,
    pub origin: Origin,
    /// The module's build script as lvm-tui read it, also where a report describes the module.
    #[serde(skip)]
    pub script: Option<ModuleScript>,
}

impl Module {
    pub fn has_plugin(&self, suffix: &str) -> bool {
        self.plugins.iter().any(|id| id == &format!("tech.annexflow.lvm.{suffix}"))
    }

    pub fn is_lvm(&self) -> bool {
        !self.plugins.is_empty()
    }
}

#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct ValueRow {
    pub key: String,
    pub value: Option<Value>,
    pub source: Source,
    pub origin: Origin,
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Serialize)]
pub struct Edge {
    pub configuration: String,
    pub target: String,
}

impl Project {
    /// Reads the build at [root]. It never runs Gradle.
    pub fn load(root: &Path) -> Self {
        let settings_file = settings_file(root);
        let settings = settings_file.as_deref().and_then(SettingsScript::read);
        let properties = Properties::read(&root.join("gradle.properties")).unwrap_or_default();
        let reports = Reports::load(root);
        let catalog_versions = catalog::versions(root);
        let root_script = ModuleScript::read(&root.join("build.gradle.kts"));

        let name = settings
            .as_ref()
            .and_then(|s| s.root_name.clone())
            .or_else(|| root.file_name().map(|n| n.to_string_lossy().into_owned()))
            .unwrap_or_default();
        let lvm = lvm_version(settings.as_ref(), root_script.as_ref());
        let options = options(settings.as_ref(), &reports);

        let mut paths: BTreeSet<String> =
            settings.iter().flat_map(|s| s.includes.iter().map(|i| i.path.clone())).collect();
        paths.extend(reports.modules.keys().cloned());
        if root_script.as_ref().is_some_and(|s| !s.lvm_plugins().is_empty()) {
            paths.insert(":".to_owned());
        }
        let accessors: BTreeMap<String, String> = paths.iter().map(|p| (accessor(p), p.clone())).collect();
        let context = FileContext { settings: settings.as_ref(), properties: &properties, accessors: &accessors };
        let modules: Vec<Module> = paths
            .iter()
            .map(|path| match reports.modules.get(path) {
                Some(report) => from_report(root, report),
                None => from_files(root, path, &context),
            })
            .collect();

        let toolchain = toolchain(root, &reports, &catalog_versions);
        let report = report_state(root, &lvm, &reports, &modules, settings_file.as_deref());
        Self {
            root: root.to_owned(),
            name,
            settings_file,
            lvm,
            options,
            toolchain,
            modules,
            report,
            reports,
            settings,
            properties,
            root_script,
        }
    }

    pub fn module(&self, path: &str) -> Option<&Module> {
        self.modules.iter().find(|m| m.path == path)
    }

    /// The version that the reports say the build runs, else the one its scripts pin.
    pub fn lvm_label(&self) -> String {
        match self.reports.modules.values().next() {
            Some(report) => report.lvm_version.clone(),
            None => self.lvm.label(),
        }
    }

    /// Modules that depend on [path] directly.
    pub fn dependents(&self, path: &str) -> Vec<String> {
        let mut dependents: Vec<String> = self
            .modules
            .iter()
            .filter(|m| m.project_dependencies.iter().any(|edge| edge.target == path))
            .map(|m| m.path.clone())
            .collect();
        dependents.dedup();
        dependents
    }

    /// Modules that a change to [path] can affect: those that depend on it, directly or through others.
    pub fn affected(&self, path: &str) -> Vec<String> {
        let mut seen = BTreeSet::new();
        let mut queue = VecDeque::from([path.to_owned()]);
        while let Some(next) = queue.pop_front() {
            for dependent in self.dependents(&next) {
                if dependent != path && seen.insert(dependent.clone()) {
                    queue.push_back(dependent);
                }
            }
        }
        seen.into_iter().collect()
    }
}

fn lvm_version(settings: Option<&SettingsScript>, root_script: Option<&ModuleScript>) -> LvmVersion {
    match settings.and_then(|s| s.lvm_plugin.as_ref()) {
        Some(pin) => match &pin.version {
            Some(version) => LvmVersion::Pinned(version.clone()),
            None if settings.is_some_and(|s| !s.plugin_builds.is_empty()) => LvmVersion::FromSource,
            None => LvmVersion::Unpinned,
        },
        None if root_script.is_some_and(|s| s.plugins.iter().any(|p| p.lvm.is_some())) => LvmVersion::Unpinned,
        None => LvmVersion::None,
    }
}

fn options(settings: Option<&SettingsScript>, reports: &Reports) -> Options {
    if let Some(reported) = reports.modules.values().find_map(|r| r.settings.clone()) {
        return Options {
            catalog: Some(reported.catalog),
            catalog_name: reported.catalog_name,
            catalog_version: Some(reported.catalog_version),
            repositories: Some(reported.repositories),
            origin: Origin::Report,
        };
    }
    let script = settings.map(|s| s.options.clone()).unwrap_or_default();
    let pinned = settings.and_then(|s| s.lvm_plugin.as_ref()).and_then(|p| p.version.clone());
    Options {
        catalog: script.catalog.or(Some(true)),
        catalog_name: script.catalog_name.unwrap_or_else(|| "lvmLibs".to_owned()),
        catalog_version: script.catalog_version.or(pinned),
        repositories: script.repositories.or(Some(true)),
        origin: Origin::Files,
    }
}

fn from_report(root: &Path, report: &ModuleReport) -> Module {
    let mut targets = Vec::new();
    if report.targets.android.is_some() {
        targets.push("android".to_owned());
    }
    if report.targets.ios.is_some() {
        targets.push("ios".to_owned());
    }
    if report.targets.jvm.is_some() {
        targets.push("jvm".to_owned());
    }
    let framework =
        report.targets.ios.as_ref().and_then(|ios| ios.framework.as_ref()).and_then(|f| f.base_name.clone());
    let edges = |map: &BTreeMap<String, Vec<String>>| -> Vec<Edge> {
        map.iter()
            .flat_map(|(configuration, targets)| {
                targets.iter().map(|target| Edge { configuration: configuration.clone(), target: target.clone() })
            })
            .collect()
    };
    let dir = root.join(&report.project_dir);
    let build_file = build_file(&dir);
    Module {
        path: report.path.clone(),
        dir: report.project_dir.clone(),
        script: build_file.as_deref().and_then(ModuleScript::read),
        build_file,
        plugins: report.plugins.clone(),
        targets,
        framework,
        layout: Layout::detect(&dir),
        values: report
            .values
            .iter()
            .map(|(key, reported)| ValueRow {
                key: key.clone(),
                value: (!reported.value.is_null()).then(|| reported.value.clone()),
                source: reported.source,
                origin: Origin::Report,
            })
            .collect(),
        project_dependencies: edges(&report.project_dependencies),
        lvm_dependencies: edges(&report.lvm_dependencies),
        source_sets: report.source_sets.clone(),
        tasks: report.tasks.clone(),
        publication: report.publication.clone(),
        origin: Origin::Report,
    }
}

struct FileContext<'a> {
    settings: Option<&'a SettingsScript>,
    properties: &'a Properties,
    /// Project paths by their typesafe accessor, such as `projects.feature.chatHistory`.
    accessors: &'a BTreeMap<String, String>,
}

fn from_files(root: &Path, path: &str, context: &FileContext) -> Module {
    let dir = path.trim_start_matches(':').replace(':', "/");
    let dir_path = root.join(&dir);
    let build_file = build_file(&dir_path);
    let script = build_file.as_deref().and_then(ModuleScript::read).unwrap_or_default();
    let plugins = script.lvm_plugins();
    let mut targets = Vec::new();
    for (on, name) in [(script.targets.android, "android"), (script.targets.ios, "ios"), (script.targets.jvm, "jvm")] {
        if on {
            targets.push(name.to_owned());
        }
    }
    let resolve = |target: &str| context.accessors.get(target).cloned().unwrap_or_else(|| target.to_owned());
    let mut project_dependencies: Vec<Edge> = script
        .project_dependencies
        .iter()
        .map(|d| Edge { configuration: d.configuration.clone(), target: resolve(&d.target) })
        .collect();
    project_dependencies.sort();
    project_dependencies.dedup();
    let mut lvm_dependencies: Vec<Edge> = script
        .lvm_dependencies
        .iter()
        .map(|d| Edge { configuration: d.configuration.clone(), target: d.target.clone() })
        .collect();
    lvm_dependencies.sort();
    Module {
        path: path.to_owned(),
        values: file_values(path, &script, &plugins, context),
        dir,
        build_file,
        plugins,
        targets,
        framework: script.targets.framework.clone(),
        layout: Layout::detect(&dir_path),
        project_dependencies,
        lvm_dependencies,
        source_sets: Vec::new(),
        tasks: Vec::new(),
        publication: None,
        origin: Origin::Files,
        script: Some(script),
    }
}

fn build_file(dir: &Path) -> Option<PathBuf> {
    ["build.gradle.kts", "build.gradle"].iter().map(|name| dir.join(name)).find(|file| file.is_file())
}

/// The sections of `lvm { }` that the module's plugins and targets use.
fn sections(plugins: &[String], script: &ModuleScript) -> BTreeSet<&'static str> {
    let mut sections = BTreeSet::new();
    for plugin in plugins {
        let added: &[&str] = match plugin.trim_start_matches("tech.annexflow.lvm.") {
            "multiplatform" | "jvm" => &["jvm", "kotlin"],
            "android.application" => &["android", "jvm", "kotlin", "application"],
            "android.library" => &["android", "jvm", "kotlin", "library"],
            "android.test" => &["android", "jvm", "kotlin"],
            "quality" => &["jvm", "kotlin", "quality"],
            "compose" => &["compose"],
            "publish" => &["publish"],
            "main-context" => &["mainContext"],
            _ => &[],
        };
        sections.extend(added);
    }
    if plugins.iter().any(|p| p.ends_with(".multiplatform")) {
        if script.targets.android {
            sections.insert("android");
        }
        if script.targets.ios {
            sections.insert("ios");
        }
    }
    sections
}

/// The values of a module without a report, layered the way blueprint layers them.
fn file_values(path: &str, script: &ModuleScript, plugins: &[String], context: &FileContext) -> Vec<ValueRow> {
    let sections = sections(plugins, script);
    let kmp_android = script.targets.android && plugins.iter().any(|p| p.ends_with(".multiplatform"));
    let mut rows: Vec<ValueRow> = keys::keys()
        .keys
        .iter()
        .filter(|key| sections.contains(key.section()))
        .filter(|key| key.key != "android.hostTests" || kmp_android)
        .map(|key| file_value(key, script, plugins, context))
        .collect();

    // Values that only a module sets, with the defaults blueprint derives for them.
    if sections.contains("android") {
        let derived = script.group.as_ref().map(|group| {
            let segments: Vec<String> =
                path.split(':').filter(|s| !s.is_empty()).map(|s| s.replace('-', "_")).collect();
            Value::String(std::iter::once(group.clone()).chain(segments).collect::<Vec<_>>().join("."))
        });
        rows.push(module_only(script, "android.namespace", derived));
    }
    if sections.contains("publish") {
        // The artifact ID: the build-wide prefix followed by the project name.
        let prefix = keys::find("publish.artifactIdPrefix")
            .and_then(|key| file_value(key, script, plugins, context).value)
            .and_then(|value| value.as_str().map(str::to_owned))
            .unwrap_or_default();
        let name = path.rsplit(':').next().unwrap_or_default();
        rows.push(module_only(script, "publish.name", Some(Value::String(format!("{prefix}{name}")))));
    }
    rows.sort_by(|a, b| a.key.cmp(&b.key));
    rows
}

/// A value without a build-wide key: the module's own, else [derived].
fn module_only(script: &ModuleScript, key: &str, derived: Option<Value>) -> ValueRow {
    let explicit = script.values.iter().rev().find(|v| v.key == key).and_then(|v| match &v.op {
        super::lvm_block::ValueOp::Set(value) => Some(value.clone()),
        super::lvm_block::ValueOp::Add(_) => None,
    });
    let source = if explicit.is_some() { Source::Module } else { Source::Default };
    ValueRow { key: key.to_owned(), value: explicit.or(derived), source, origin: Origin::Files }
}

fn file_value(key: &Key, script: &ModuleScript, plugins: &[String], context: &FileContext) -> ValueRow {
    let default = default_value(key, script, plugins, context);
    let settings_values = context.settings.map(|s| s.values.as_slice()).unwrap_or_default();
    let from_settings = apply_writes(settings_values, &key.key, None).map(|v| key.coerce(v));
    let from_property = context
        .properties
        .get(&format!("lvm.{}", key.key))
        .map(|raw| key.parse(raw).unwrap_or_else(|_| Value::String(raw.to_owned())));
    let (build_wide, layer) = match (from_property, from_settings) {
        (Some(value), _) => (Some(value), Source::Property),
        (None, Some(value)) => (Some(value), Source::Settings),
        (None, None) => (default, Source::Default),
    };

    let module_writes = !key.build_wide_only && script.values.iter().any(|v| v.key == key.key);
    if !module_writes {
        return ValueRow { key: key.key.clone(), value: build_wide, source: layer, origin: Origin::Files };
    }
    let value = match key.module {
        // A module's list is appended to the build-wide one.
        Some(ListMode::Append) => {
            apply_writes(&script.values, &key.key, Some(build_wide.unwrap_or(Value::Array(Vec::new()))))
        }
        _ => apply_writes(&script.values, &key.key, None),
    }
    .map(|v| key.coerce(v));
    ValueRow { key: key.key.clone(), value, source: Source::Module, origin: Origin::Files }
}

/// The default that blueprint uses for [key], including the ones it derives from other values.
fn default_value(key: &Key, script: &ModuleScript, plugins: &[String], context: &FileContext) -> Option<Value> {
    let effective = |other: &str| keys::find(other).and_then(|other| file_value(other, script, plugins, context).value);
    match key.key.as_str() {
        // The flat layout analyzes its own directories.
        "quality.source" => {
            if effective("kotlin.flatLayout") == Some(Value::Bool(true)) {
                Some(serde_json::json!([
                    "src",
                    "test",
                    "src@*",
                    "test@*",
                    "androidTest",
                    "androidTest@*",
                    "testFixtures",
                    "testFixtures@*"
                ]))
            } else {
                key.default.clone()
            }
        }
        // A Compose module gets the Compose rules.
        "quality.composeRules" if plugins.iter().any(|p| p.ends_with(".compose")) => Some(Value::Bool(true)),
        // The source repository of the POM follows the project's web page.
        "publish.scm.url" => effective("publish.url"),
        "publish.scm.connection" => effective("publish.scm.url")
            .and_then(|url| url.as_str().map(|url| Value::String(format!("scm:git:{url}.git")))),
        "publish.scm.developerConnection" => effective("publish.scm.connection"),
        _ => key.default.clone(),
    }
}

/// `:feature:chat-history` -> `projects.feature.chatHistory`, the accessor Gradle generates for it.
fn accessor(path: &str) -> String {
    let segments = path.split(':').filter(|s| !s.is_empty()).map(|segment| {
        let mut camel = String::new();
        let mut upper = false;
        for c in segment.chars() {
            if c == '-' || c == '_' {
                upper = true;
            } else if upper {
                camel.extend(c.to_uppercase());
                upper = false;
            } else {
                camel.push(c);
            }
        }
        camel
    });
    std::iter::once("projects".to_owned()).chain(segments).collect::<Vec<_>>().join(".")
}

fn toolchain(root: &Path, reports: &Reports, catalog_versions: &BTreeMap<String, String>) -> Vec<Tool> {
    let reported =
        |name: &str| -> Option<ToolVersions> { reports.modules.values().find_map(|r| r.toolchain.get(name).cloned()) };
    let minimums = &keys::keys().minimums;
    let file_tool = |name: &str, actual: Option<String>, minimum: &str| Tool {
        name: name.to_owned(),
        actual,
        minimum: Some(minimum.to_owned()),
        tested: None,
        origin: Origin::Files,
    };
    [
        ("gradle", wrapper::gradle_version(root), minimums.gradle.as_str()),
        ("kotlin", catalog_versions.get("kotlin").cloned(), minimums.kotlin.as_str()),
        ("agp", catalog_versions.get("agp").cloned(), minimums.agp.as_str()),
    ]
    .into_iter()
    .map(|(name, actual, minimum)| match reported(name) {
        Some(versions) => Tool {
            name: name.to_owned(),
            actual: Some(versions.actual),
            minimum: Some(versions.minimum),
            tested: Some(versions.tested),
            origin: Origin::Report,
        },
        None => file_tool(name, actual, minimum),
    })
    .collect()
}

fn report_state(
    root: &Path,
    lvm: &LvmVersion,
    reports: &Reports,
    modules: &[Module],
    settings_file: Option<&Path>,
) -> ReportState {
    if reports.is_empty() {
        return match lvm {
            LvmVersion::Pinned(version) if version::compare(version, REPORT_SINCE).is_lt() => {
                ReportState::Unsupported { version: version.clone() }
            }
            _ => ReportState::Missing,
        };
    }
    let missing_module = modules.iter().any(|m| m.origin == Origin::Files && m.is_lvm());
    let written = reports.written.unwrap_or(SystemTime::UNIX_EPOCH);
    let scripts = [
        settings_file.map(Path::to_path_buf),
        Some(root.join("gradle.properties")),
        Some(root.join("build.gradle.kts")),
    ]
    .into_iter()
    .flatten()
    .chain(modules.iter().filter_map(|m| m.build_file.clone()));
    let changed = scripts
        .filter_map(|file| fs::metadata(file).and_then(|m| m.modified()).ok())
        .any(|modified| modified > written);
    if missing_module || changed { ReportState::Stale } else { ReportState::Fresh }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write(root: &Path, file: &str, text: &str) {
        let path = root.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    fn row<'a>(module: &'a Module, key: &str) -> &'a ValueRow {
        module.values.iter().find(|row| row.key == key).unwrap_or_else(|| panic!("no {key}"))
    }

    fn consumer() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(
            root,
            "settings.gradle.kts",
            r#"plugins { id("tech.annexflow.lvm.settings") version "0.3.2" }
lvm {
    android { minSdk = 26; compileSdk = 36 }
    kotlin { optIns.add("a.Settings") }
}
include(":shared", ":core-data")
"#,
        );
        write(root, "gradle.properties", "lvm.android.compileSdk=37\nlvm.kotlin.flatLayout=true\n");
        write(
            root,
            "shared/build.gradle.kts",
            r#"plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
    alias(lvmLibs.plugins.blueprint.quality)
}
group = "com.example"
lvm {
    targets { android(); jvm() }
    android { minSdk = 28 }
    kotlin { optIns.add("b.Module") }
}
kotlin { sourceSets.commonMain.dependencies { implementation(projects.coreData) } }
"#,
        );
        write(
            root,
            "core-data/build.gradle.kts",
            "plugins { alias(lvmLibs.plugins.blueprint.multiplatform) }\nlvm { targets { jvm() } }\n",
        );
        dir
    }

    #[test]
    fn layers_values_from_files_as_blueprint_does() {
        let dir = consumer();
        let project = Project::load(dir.path());
        let shared = project.module(":shared").unwrap();

        assert_eq!(shared.origin, Origin::Files);
        assert_eq!(row(shared, "android.minSdk").value, Some(28.into()));
        assert_eq!(row(shared, "android.minSdk").source, Source::Module);
        assert_eq!(row(shared, "android.compileSdk").value, Some(37.into()));
        assert_eq!(row(shared, "android.compileSdk").source, Source::Property);
        assert_eq!(row(shared, "android.targetSdk").source, Source::Default);
        assert_eq!(row(shared, "kotlin.optIns").value, Some(serde_json::json!(["a.Settings", "b.Module"])));
        assert_eq!(row(shared, "kotlin.optIns").source, Source::Module);
        assert_eq!(row(shared, "android.namespace").value, Some("com.example.shared".into()));
        assert_eq!(row(shared, "quality.source").value.as_ref().and_then(|v| v.as_array()).map(Vec::len), Some(8));
        assert!(shared.values.iter().all(|row| !row.key.starts_with("ios.")));
    }

    #[test]
    fn resolves_typesafe_accessors_and_the_reverse_graph() {
        let dir = consumer();
        let project = Project::load(dir.path());
        let shared = project.module(":shared").unwrap();

        assert_eq!(
            shared.project_dependencies,
            [Edge { configuration: "commonMainImplementation".into(), target: ":core-data".into() }]
        );
        assert_eq!(project.dependents(":core-data"), [":shared"]);
        assert_eq!(project.affected(":core-data"), [":shared"]);
        assert!(project.affected(":shared").is_empty());
    }

    #[test]
    fn a_version_before_lvm_report_cannot_have_reports() {
        let dir = consumer();
        let project = Project::load(dir.path());
        assert_eq!(project.lvm, LvmVersion::Pinned("0.3.2".into()));
        assert_eq!(project.report, ReportState::Unsupported { version: "0.3.2".into() });
    }

    #[test]
    fn reports_win_over_files_and_go_stale_when_a_script_changes() {
        let dir = consumer();
        let root = dir.path();
        let example = include_str!("../../../blueprint/multiplatform/src/test/resources/lvm-report.json");
        write(
            root,
            "build/lvm/report/shared/project.json",
            &example.replace("\":lib\"", "\":shared\"").replace("\"lib\"", "\"shared\""),
        );

        let project = Project::load(root);
        let shared = project.module(":shared").unwrap();
        assert_eq!(shared.origin, Origin::Report);
        assert_eq!(row(shared, "jvm.target").value, Some(17.into()));
        // :core-data has a plugin but no report yet.
        assert_eq!(project.report, ReportState::Stale);

        let core = example.replace("\":lib\"", "\":core-data\"").replace("\"lib\"", "\"core-data\"");
        write(root, "build/lvm/report/core-data/project.json", &core);
        let past = SystemTime::now() - std::time::Duration::from_secs(60);
        for script in
            ["settings.gradle.kts", "gradle.properties", "shared/build.gradle.kts", "core-data/build.gradle.kts"]
        {
            fs::File::options().write(true).open(root.join(script)).unwrap().set_modified(past).unwrap();
        }
        assert_eq!(Project::load(root).report, ReportState::Fresh);

        fs::File::options()
            .write(true)
            .open(root.join("shared/build.gradle.kts"))
            .unwrap()
            .set_modified(SystemTime::now() + std::time::Duration::from_secs(60))
            .unwrap();
        assert_eq!(Project::load(root).report, ReportState::Stale);
    }

    #[test]
    fn accessors_follow_gradle_naming() {
        assert_eq!(accessor(":feature:chat-history"), "projects.feature.chatHistory");
        assert_eq!(accessor(":core_data"), "projects.coreData");
    }
}
