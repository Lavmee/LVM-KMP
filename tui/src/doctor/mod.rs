//! Checks a build for what makes LVM's plugins fail or misbehave, before Gradle runs, and fixes what it can.

pub mod env;

use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

use anyhow::{Result, bail};
use serde::Serialize;
use serde_json::Value;

use crate::edit::{ChangeSet, TextEdit};
use crate::migrate;
use crate::project::catalog::{BLUEPRINT_ALIASES, alias_to_id, id_to_alias};
use crate::project::keys;
use crate::project::kts_edit;
use crate::project::lvm_block::ScriptValue;
use crate::project::model::{Module, Project};
use crate::project::module::Layout;
use crate::version::Support;
pub use env::Environment;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Severity {
    Error,
    Warning,
    Info,
}

impl Severity {
    pub fn label(self) -> &'static str {
        match self {
            Self::Error => "error",
            Self::Warning => "warning",
            Self::Info => "info",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Finding {
    pub rule: &'static str,
    pub severity: Severity,
    pub message: String,
    /// The file to change, relative to the build root.
    pub file: Option<PathBuf>,
    pub line: Option<usize>,
    pub module: Option<String>,
    pub fix: Option<Fix>,
}

/// A change that resolves a finding.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum Fix {
    /// Adds plugin declarations with `apply false` to the root build script.
    DeclareRootPlugins { lines: Vec<String> },
    /// Renames a key in `gradle.properties`.
    RenameProperty { line: usize, from: String, to: String },
    /// Adds `group = "..."` to a module.
    SetGroup { module: String, group: String },
    /// Moves a module's sources to the flat layout.
    MigrateFlatLayout { module: String },
    /// Removes `local.properties` from git and ignores it.
    UntrackLocalProperties,
}

impl Fix {
    pub fn label(&self) -> String {
        match self {
            Self::DeclareRootPlugins { lines } => format!("declare {} plugin(s) in the root build script", lines.len()),
            Self::RenameProperty { to, .. } => format!("rename it to lvm.{to}"),
            Self::SetGroup { group, .. } => format!("set group = \"{group}\""),
            Self::MigrateFlatLayout { .. } => "move its sources to the flat layout".to_owned(),
            Self::UntrackLocalProperties => "untrack it and add it to .gitignore".to_owned(),
        }
    }

    /// Adds the fix to [changes], on top of what they already change.
    pub fn apply(&self, project: &Project, changes: &mut ChangeSet) -> Result<()> {
        let root = &project.root;
        match self {
            Self::DeclareRootPlugins { lines } => {
                let file = Path::new("build.gradle.kts");
                for line in lines {
                    let source = changes.current(root, file);
                    changes.edit(root, file, &[kts_edit::add_statement(&source, &["plugins"], line)]);
                }
            }
            Self::RenameProperty { line, from, to } => {
                let file = Path::new("gradle.properties");
                let source = changes.current(root, file);
                let Some(range) = line_range(&source, *line) else { bail!("gradle.properties has no line {line}") };
                let text = source[range.clone()].replacen(&format!("lvm.{from}"), &format!("lvm.{to}"), 1);
                changes.edit(root, file, &[TextEdit::replace(range, text)]);
            }
            Self::SetGroup { module, group } => {
                let module = project.module(module).ok_or_else(|| anyhow::anyhow!("no module {module}"))?;
                let Some(build_file) = &module.build_file else { bail!("{} has no build script", module.path) };
                let file = build_file.strip_prefix(root).unwrap_or(build_file);
                let source = changes.current(root, file);
                changes.edit(
                    root,
                    file,
                    &kts_edit::set_value(&source, &[], "group", &kts_edit::kotlin_literal(&group.as_str().into())),
                );
            }
            Self::MigrateFlatLayout { module } => {
                let module = project.module(module).ok_or_else(|| anyhow::anyhow!("no module {module}"))?;
                migrate::flat_layout(project, module, changes)?;
            }
            Self::UntrackLocalProperties => {
                let file = Path::new(".gitignore");
                let source = changes.current(root, file);
                if !source.lines().any(|line| line.trim().trim_start_matches('/') == "local.properties") {
                    let separator = if source.is_empty() || source.ends_with('\n') { "" } else { "\n" };
                    changes.edit(
                        root,
                        file,
                        &[TextEdit::insert(source.len(), format!("{separator}local.properties\n"))],
                    );
                }
                changes.untrack.push(PathBuf::from("local.properties"));
            }
        }
        Ok(())
    }
}

/// The bytes of 1-based [line], without its line break.
fn line_range(source: &str, line: usize) -> Option<std::ops::Range<usize>> {
    let start = if line == 1 { 0 } else { source.match_indices('\n').nth(line - 2)?.0 + 1 };
    let end = source[start..].find('\n').map_or(source.len(), |i| start + i);
    Some(start..end)
}

/// Every finding for [project], errors first.
pub fn diagnose(project: &Project, env: &Environment) -> Vec<Finding> {
    let mut findings = Vec::new();
    root_plugins(project, &mut findings);
    settings_options(project, &mut findings);
    unknown_and_invalid_values(project, &mut findings);
    for module in project.modules.iter().filter(|m| m.is_lvm()) {
        namespace(project, module, &mut findings);
        flat_layout(project, module, &mut findings);
        main_context(project, module, &mut findings);
        storage_serialization(module, &mut findings);
        test_runner(project, module, &mut findings);
    }
    toolchain(project, &mut findings);
    environment(project, env, &mut findings);
    publishing(project, &mut findings);
    findings.sort_by(|a, b| (a.severity, &a.file, a.line, a.rule).cmp(&(b.severity, &b.file, b.line, b.rule)));
    findings
}

fn relative(project: &Project, file: &Path) -> PathBuf {
    file.strip_prefix(&project.root).unwrap_or(file).to_owned()
}

fn finding(rule: &'static str, severity: Severity, message: impl Into<String>) -> Finding {
    Finding { rule, severity, message: message.into(), file: None, line: None, module: None, fix: None }
}

impl Finding {
    fn at(mut self, file: impl Into<PathBuf>, line: Option<usize>) -> Self {
        self.file = Some(crate::edit::portable(&file.into()));
        self.line = line;
        self
    }

    fn in_module(mut self, module: &Module) -> Self {
        self.module = Some(module.path.clone());
        self
    }

    fn with_fix(mut self, fix: Fix) -> Self {
        self.fix = Some(fix);
        self
    }
}

/// The plugins that the root build script must put on the build classpath for the modules' LVM plugins, by alias.
fn required_root_plugins(project: &Project) -> BTreeSet<&'static str> {
    project
        .modules
        .iter()
        .flat_map(|module| required_aliases(&module.plugins, module.targets.iter().any(|t| t == "android")))
        .collect()
}

/// The plugins, by alias, that the root build script declares for a module with LVM's [plugins], for example
/// `kotlin.multiplatform` for `tech.annexflow.lvm.multiplatform`.
pub fn required_aliases(plugins: &[String], android_target: bool) -> BTreeSet<&'static str> {
    let has = |suffix: &str| plugins.iter().any(|id| *id == format!("tech.annexflow.lvm.{suffix}"));
    let kmp = has("multiplatform");
    let mut required: BTreeSet<&'static str> = plugins
        .iter()
        .filter_map(|plugin| BLUEPRINT_ALIASES.iter().find(|(_, id)| id == plugin).map(|(alias, _)| *alias))
        .collect();
    if kmp {
        required.insert("kotlin.multiplatform");
        if android_target {
            required.insert("android.kmp.library");
        }
    }
    let external = [
        ("android.application", "android.application"),
        ("android.library", "android.library"),
        ("android.test", "android.test"),
        ("jvm", "kotlin.jvm"),
        ("compose", "kotlin.compose"),
        ("quality", "detekt"),
        ("publish", "vanniktech.mavenPublish"),
    ];
    for (plugin, alias) in external {
        if has(plugin) {
            required.insert(alias);
        }
    }
    if kmp && has("compose") {
        required.insert("compose.multiplatform");
    }
    required
}

/// The lines that declare [aliases] in the root build script with `apply false`, for those it doesn't declare yet
/// and that lvm-tui can write: without the catalog, only LVM's own plugins, whose version the settings plugin sets.
pub fn missing_root_lines(project: &Project, aliases: &BTreeSet<&str>) -> Vec<String> {
    let declared = declared_root_plugins(project);
    aliases.iter().filter(|alias| !declared.contains(**alias)).filter_map(|alias| root_line(project, alias)).collect()
}

fn root_line(project: &Project, alias: &str) -> Option<String> {
    match (project.options.catalog != Some(false)).then_some(project.options.catalog_name.as_str()) {
        Some(catalog) => Some(format!("alias({catalog}.plugins.{alias}) apply false")),
        None => {
            alias_to_id(alias).filter(|_| alias.starts_with("blueprint.")).map(|id| format!("id(\"{id}\") apply false"))
        }
    }
}

/// The aliases that the root build script declares, from `alias(...)`, `id(...)` and `kotlin(...)`.
fn declared_root_plugins(project: &Project) -> BTreeSet<String> {
    let Some(script) = &project.root_script else { return BTreeSet::new() };
    script.plugins.iter().filter_map(|plugin| id_to_alias(plugin.id.as_deref()?)).collect()
}

fn root_plugins(project: &Project, findings: &mut Vec<Finding>) {
    let declared = declared_root_plugins(project);
    let missing: Vec<&str> =
        required_root_plugins(project).into_iter().filter(|alias| !declared.contains(*alias)).collect();
    if missing.is_empty() {
        return;
    }
    let line = |alias: &str| root_line(project, alias);
    let (blueprint, external): (Vec<&str>, Vec<&str>) =
        missing.iter().partition(|alias| alias.starts_with("blueprint."));
    let file =
        project.root_script.as_ref().map_or_else(|| PathBuf::from("build.gradle.kts"), |s| relative(project, &s.file));
    let plugins_line = project.root_script.as_ref().and_then(|s| s.plugins.first().map(|p| p.line));
    let mut push = |aliases: &[&str], severity: Severity, why: &str| {
        if aliases.is_empty() {
            return;
        }
        let names: Vec<String> = aliases.iter().map(|alias| alias_to_id(alias).unwrap_or(alias).to_owned()).collect();
        let message = format!("The root build script doesn't declare {} with apply false: {why}", names.join(", "));
        let mut f = finding("root-plugins", severity, message).at(file.clone(), plugins_line);
        let lines: Vec<String> = aliases.iter().filter_map(|alias| line(alias)).collect();
        if !lines.is_empty() {
            f = f.with_fix(Fix::DeclareRootPlugins { lines });
        }
        findings.push(f);
    };
    push(&blueprint, Severity::Error, "all of LVM's plugins must load in one class loader");
    push(&external, Severity::Warning, "LVM's plugins need them on the build classpath, unless build logic adds them");
}

fn settings_options(project: &Project, findings: &mut Vec<Finding>) {
    let Some(settings) = &project.settings else { return };
    if settings.options.catalog_name.as_deref() == Some("lvm") {
        let line = settings.options.lines.get("catalogName").copied();
        findings.push(
            finding(
                "catalog-name",
                Severity::Error,
                "catalogName can't be \"lvm\": that name is the lvm { } block of every project",
            )
            .at(relative(project, &settings.file), line),
        );
    }
}

/// Keys that only a module's `lvm { }` block sets, without a build-wide `lvm.*` property.
const MODULE_ONLY: &[&str] = &[
    "android.namespace",
    "publish.name",
    "compose.metricsDirectory",
    "compose.reportsDirectory",
    "kotlin.excludedOptIns",
];

fn unknown_and_invalid_values(project: &Project, findings: &mut Vec<Finding>) {
    for entry in &project.properties.entries {
        let Some(key) = entry.key.strip_prefix("lvm.") else { continue };
        if key.starts_with("settings.") {
            continue;
        }
        match keys::find(key) {
            Some(known) => {
                if let Err(error) = known.parse(&entry.value) {
                    findings.push(
                        finding("invalid-value", Severity::Error, error).at("gradle.properties", Some(entry.line)),
                    );
                }
            }
            None => {
                let suggestion = suggest(key);
                let mut message = format!("lvm.{key} is not a key of LVM, so nothing reads it");
                if let Some(suggestion) = &suggestion {
                    message.push_str(&format!("; did you mean lvm.{suggestion}?"));
                }
                let mut f =
                    finding("unknown-key", Severity::Warning, message).at("gradle.properties", Some(entry.line));
                if let Some(to) = suggestion {
                    f = f.with_fix(Fix::RenameProperty { line: entry.line, from: key.to_owned(), to });
                }
                findings.push(f);
            }
        }
    }
    if let Some(settings) = &project.settings {
        script_keys(project, &settings.file, &settings.values, &[], findings);
    }
    for module in &project.modules {
        if let Some(script) = &module.script {
            script_keys(project, &script.file, &script.values, MODULE_ONLY, findings);
        }
    }
}

fn script_keys(project: &Project, file: &Path, values: &[ScriptValue], allowed: &[&str], findings: &mut Vec<Finding>) {
    for value in values {
        if allowed.contains(&value.key.as_str()) {
            continue;
        }
        match keys::find(&value.key) {
            None => {
                let hint = suggest(&value.key).map(|s| format!("; did you mean {s}?")).unwrap_or_default();
                let message = format!("lvm {{ }} sets {}, which LVM doesn't have{hint}", value.key);
                findings.push(
                    finding("unknown-key", Severity::Warning, message).at(relative(project, file), Some(value.line)),
                );
            }
            Some(key) => {
                // values.put("android.minSdk", "abc") passes a string that blueprint parses later.
                if let crate::project::lvm_block::ValueOp::Set(Value::String(raw)) = &value.op
                    && key.kind != keys::KeyType::String
                    && let Err(error) = key.parse(raw)
                {
                    findings.push(
                        finding("invalid-value", Severity::Error, error).at(relative(project, file), Some(value.line)),
                    );
                }
            }
        }
    }
}

/// The closest key of LVM to [key], when it is close enough to be a typo.
fn suggest(key: &str) -> Option<String> {
    let lower = key.to_lowercase();
    keys::keys()
        .keys
        .iter()
        .map(|known| (distance(&lower, &known.key.to_lowercase()), &known.key))
        .filter(|(distance, _)| *distance <= 3)
        .min_by_key(|(distance, _)| *distance)
        .map(|(_, known)| known.clone())
}

/// Levenshtein distance.
fn distance(a: &str, b: &str) -> usize {
    let b: Vec<char> = b.chars().collect();
    let mut previous: Vec<usize> = (0..=b.len()).collect();
    for (i, ca) in a.chars().enumerate() {
        let mut current = vec![i + 1];
        for (j, cb) in b.iter().enumerate() {
            let substitution = previous[j] + usize::from(ca != *cb);
            current.push(substitution.min(previous[j + 1] + 1).min(current[j] + 1));
        }
        previous = current;
    }
    previous[b.len()]
}

fn has_android(module: &Module) -> bool {
    module.has_plugin("android.application")
        || module.has_plugin("android.library")
        || module.has_plugin("android.test")
        || (module.has_plugin("multiplatform") && module.targets.iter().any(|t| t == "android"))
}

fn value<'a>(module: &'a Module, key: &str) -> Option<&'a Value> {
    module.values.iter().find(|row| row.key == key).and_then(|row| row.value.as_ref())
}

fn build_file(project: &Project, module: &Module) -> Option<PathBuf> {
    module.build_file.as_deref().map(|file| relative(project, file))
}

fn namespace(project: &Project, module: &Module, findings: &mut Vec<Finding>) {
    if !has_android(module) || value(module, "android.namespace").is_some() {
        return;
    }
    if module.script.as_ref().is_some_and(|s| s.native_namespace || s.group.is_some()) {
        return;
    }
    let message = format!(
        "{} has Android but no namespace: set group = \"...\" in the module, or lvm {{ android {{ namespace = \"...\" }} }}",
        module.path
    );
    let mut f = finding("namespace", Severity::Error, message).in_module(module);
    if let Some(file) = build_file(project, module) {
        f = f.at(file, None);
    }
    // The group most other modules use is the likely one.
    let mut groups: BTreeMap<&str, usize> = BTreeMap::new();
    for group in project.modules.iter().filter_map(|m| m.script.as_ref()?.group.as_deref()) {
        *groups.entry(group).or_default() += 1;
    }
    if let Some((group, _)) = groups.into_iter().max_by_key(|(_, count)| *count) {
        f = f.with_fix(Fix::SetGroup { module: module.path.clone(), group: group.to_owned() });
    }
    findings.push(f);
}

fn flat_layout(project: &Project, module: &Module, findings: &mut Vec<Finding>) {
    if value(module, "kotlin.flatLayout") != Some(&Value::Bool(true))
        || !matches!(module.layout, Layout::Standard | Layout::Mixed)
    {
        return;
    }
    let message = format!(
        "{} uses the flat source layout, but still has sources in src/<sourceSet>/kotlin, which fails the build",
        module.path
    );
    let mut f = finding("flat-layout", Severity::Error, message).in_module(module);
    if let Some(file) = build_file(project, module) {
        f = f.at(file, None);
    }
    findings.push(f.with_fix(Fix::MigrateFlatLayout { module: module.path.clone() }));
}

fn tool<'a>(project: &'a Project, name: &str) -> Option<&'a crate::project::model::Tool> {
    project.toolchain.iter().find(|tool| tool.name == name)
}

fn main_context(project: &Project, module: &Module, findings: &mut Vec<Finding>) {
    if !module.has_plugin("main-context") {
        return;
    }
    let file = build_file(project, module);
    if let Some(kotlin) = tool(project, "kotlin")
        && let (Some(actual), Some(tested)) = (&kotlin.actual, &kotlin.tested)
        && bugfix_line(actual) != bugfix_line(tested)
    {
        let message = format!(
            "{} applies tech.annexflow.lvm.main-context, whose compiler plugin runs only on Kotlin {}.x like {tested}; the build uses {actual}",
            module.path,
            tested.rsplit_once('.').map_or(tested.as_str(), |(line, _)| line),
        );
        let mut f = finding("main-context-kotlin", Severity::Error, message).in_module(module);
        if let Some(file) = &file {
            f = f.at(file.clone(), None);
        }
        findings.push(f);
    }

    // A library leaves the main dispatcher to the app, and its tests set one; only a JVM app needs its own.
    let platforms = value(module, "mainContext.platforms").and_then(Value::as_array);
    let on_jvm = platforms.is_none_or(|p| p.iter().any(|v| v == "jvm"));
    let source = module.script.as_ref().map(|s| s.source.as_str()).unwrap_or_default();
    let jvm_app = module.script.as_ref().is_some_and(|s| s.plugins.iter().any(|p| p.notation == "id:application"))
        || source.contains("mainClass");
    let dispatcher =
        ["coroutines-swing", "coroutines.swing", "coroutines-javafx", "coroutines.javafx", "compose.desktop"]
            .iter()
            .any(|name| source.contains(name));
    if on_jvm && jvm_app && !dispatcher && !module.has_plugin("compose") {
        let message = format!(
            "{} is a JVM app that runs @MainContext calls on the main dispatcher, which needs kotlinx-coroutines-swing or -javafx",
            module.path
        );
        let mut f = finding("main-context-dispatcher", Severity::Warning, message).in_module(module);
        if let Some(file) = file {
            f = f.at(file, None);
        }
        findings.push(f);
    }
}

/// `2.4.21` -> `(2, 4, 2)`: bugfix releases share the major, the minor and the patch without its last digit.
fn bugfix_line(version: &str) -> Option<(u64, u64, u64)> {
    let mut parts = version.split('-').next()?.split('.').map(|p| p.parse::<u64>().ok());
    Some((parts.next()??, parts.next()??, parts.next()?? / 10))
}

fn storage_serialization(module: &Module, findings: &mut Vec<Finding>) {
    if !module.lvm_dependencies.iter().any(|d| d.target == "lvm-storage") {
        return;
    }
    let serialization =
        module.script.as_ref().is_some_and(|s| s.plugins.iter().any(|p| p.notation.contains("serialization")));
    if !serialization {
        let message = format!(
            "{} uses lvm-storage, whose serializers store @Serializable values: apply the Kotlin serialization plugin where they are declared",
            module.path
        );
        findings.push(finding("storage-serialization", Severity::Info, message).in_module(module));
    }
}

fn test_runner(project: &Project, module: &Module, findings: &mut Vec<Finding>) {
    if !has_android(module) {
        return;
    }
    let dir = project.root.join(&module.dir);
    let has_instrumented_tests = std::fs::read_dir(&dir)
        .into_iter()
        .flatten()
        .chain(std::fs::read_dir(dir.join("src")).into_iter().flatten())
        .filter_map(Result::ok)
        .any(|entry| entry.path().is_dir() && entry.file_name().to_string_lossy().starts_with("androidTest"));
    let source = module.script.as_ref().map(|s| s.source.as_str()).unwrap_or_default();
    let runner = ["androidx.test", "espresso", "androidx-test"].iter().any(|name| source.contains(name));
    if has_instrumented_tests && !runner {
        let message = format!(
            "{} has instrumented tests but no androidx.test:runner or Espresso, which the test runner needs",
            module.path
        );
        // lvm-tui can't see what a convention plugin adds, so with one the dependencies may well be there.
        let severity = if applies_unknown_plugins(module) { Severity::Info } else { Severity::Warning };
        let mut f = finding("test-runner", severity, message).in_module(module);
        if let Some(file) = build_file(project, module) {
            f = f.at(file, None);
        }
        findings.push(f);
    }
}

/// Whether [module] applies a plugin that is neither LVM's, Kotlin's nor one that LVM's plugins need, such as a
/// convention plugin of the build's own build logic, which can add dependencies that the module's script doesn't show.
fn applies_unknown_plugins(module: &Module) -> bool {
    let Some(script) = &module.script else { return false };
    script.plugins.iter().filter(|plugin| plugin.applied).any(|plugin| {
        plugin.id.as_deref().is_none_or(|id| {
            !id.starts_with("tech.annexflow.lvm.")
                && !id.starts_with("org.jetbrains.kotlin.")
                && id_to_alias(id).is_none()
        })
    })
}

fn toolchain(project: &Project, findings: &mut Vec<Finding>) {
    for tool in &project.toolchain {
        let name = match tool.name.as_str() {
            "gradle" => "Gradle",
            "kotlin" => "Kotlin",
            "agp" => "AGP",
            other => other,
        };
        let actual = tool.actual.as_deref().unwrap_or("?");
        let (severity, message) = match tool.support() {
            Support::TooOld => (
                Severity::Error,
                format!(
                    "LVM needs {name} {} or newer; the build uses {actual}",
                    tool.minimum.as_deref().unwrap_or("?")
                ),
            ),
            Support::NewerThanTested => (
                Severity::Warning,
                format!(
                    "LVM was tested with {name} {}; the build uses the newer {actual}",
                    tool.tested.as_deref().unwrap_or("?")
                ),
            ),
            Support::Supported | Support::Unknown => continue,
        };
        let mut f = finding("toolchain", severity, message);
        if tool.name == "gradle" {
            f = f.at("gradle/wrapper/gradle-wrapper.properties", None);
        }
        findings.push(f);
    }
}

fn environment(project: &Project, env: &Environment, findings: &mut Vec<Finding>) {
    let lvm: Vec<&Module> = project.modules.iter().filter(|m| m.is_lvm()).collect();
    if lvm.iter().any(|m| has_android(m)) && env.android_sdk.is_none() {
        findings.push(finding(
            "android-sdk",
            Severity::Error,
            "No Android SDK: set ANDROID_HOME, or sdk.dir in local.properties",
        ));
    }
    if lvm.iter().any(|m| m.targets.iter().any(|t| t == "ios")) {
        match (env.os.as_str(), env.xcode) {
            ("macos", Some(false)) => findings.push(finding(
                "xcode",
                Severity::Warning,
                "iOS targets need Xcode, and xcode-select -p finds none: install Xcode or run xcode-select -s",
            )),
            ("macos", _) => {}
            (os, _) => {
                findings.push(finding("xcode", Severity::Info, format!("iOS targets build only on macOS, not on {os}")))
            }
        }
    }
    jdk(project, env, &lvm, findings);
    if env.local_properties_tracked {
        findings.push(
            finding(
                "local-properties",
                Severity::Error,
                "git tracks local.properties, which holds machine paths and may hold secrets",
            )
            .at("local.properties", None)
            .with_fix(Fix::UntrackLocalProperties),
        );
    }
}

fn jdk(project: &Project, env: &Environment, lvm: &[&Module], findings: &mut Vec<Finding>) {
    let needed = lvm.iter().filter_map(|m| value(m, "jvm.target")?.as_u64()).max();
    let Some(needed) = needed else { return };
    let daemon =
        crate::project::properties::Properties::read(&project.root.join("gradle/gradle-daemon-jvm.properties"))
            .and_then(|p| p.get("toolchainVersion").and_then(|v| v.parse::<u64>().ok()));
    match (daemon, env.java) {
        // Gradle finds or downloads the daemon's JDK itself.
        (Some(daemon), _) if daemon < needed => findings.push(
            finding(
                "jdk",
                Severity::Warning,
                format!(
                    "The Gradle daemon runs on JDK {daemon}, older than jvm.target {needed}, so it can't compile for it"
                ),
            )
            .at("gradle/gradle-daemon-jvm.properties", None),
        ),
        (Some(_), _) => {}
        (None, Some(java)) if u64::from(java) < needed => findings.push(finding(
            "jdk",
            Severity::Warning,
            format!("JAVA_HOME or the PATH points to JDK {java}, older than jvm.target {needed}"),
        )),
        (None, None) => findings.push(finding("jdk", Severity::Warning, "No JDK found: set JAVA_HOME")),
        (None, Some(_)) => {}
    }
}

/// Secrets in the build's `gradle.properties`, and POMs that Maven Central would reject.
fn publishing(project: &Project, findings: &mut Vec<Finding>) {
    for key in crate::library::SECRETS {
        if let Some(entry) = project.properties.entry(key).filter(|e| !e.value.is_empty()) {
            let message = format!(
                "{key} is in the build's gradle.properties, which git shares: move it to ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_{key}"
            );
            findings
                .push(finding("publish-secrets", Severity::Error, message).at("gradle.properties", Some(entry.line)));
        }
    }
    for module in project.modules.iter().filter(|m| m.has_plugin("publish")) {
        let missing: Vec<String> = crate::library::REQUIRED_POM
            .iter()
            .filter(|key| value(module, key).is_none_or(|v| v.as_str() == Some("")))
            .map(|key| format!("lvm.{key}"))
            .collect();
        if !missing.is_empty() {
            let message = format!(
                "The POM of {} needs {}: set them in lvm {{ publish {{ }} }} of settings.gradle.kts or in gradle.properties",
                module.path,
                missing.join(", ")
            );
            findings.push(finding("pom", Severity::Error, message).in_module(module));
        }
    }
}

/// Whether every error of [findings] is fixed, for the exit code of `lvm-tui check`.
pub fn has_errors(findings: &[Finding]) -> bool {
    findings.iter().any(|f| f.severity == Severity::Error)
}

#[cfg(test)]
mod tests;
