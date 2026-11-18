//! The Compose compiler's reports of a module: how stable its classes are, which composables skip, and the metrics of
//! each compilation. `tech.annexflow.lvm.compose` writes them when `lvm.compose.reports` and `lvm.compose.metrics`
//! are on.

use std::fs;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use serde_json::Value;

use crate::project::model::Module;

/// Where the compiler writes reports and metrics when a module doesn't say otherwise, relative to the module.
pub const REPORTS_DIR: &str = "build/compose-reports";
pub const METRICS_DIR: &str = "build/compose-metrics";

/// The Gradle properties that turn the reports on for one build, without changing a file.
pub const PROPERTIES: [&str; 2] = ["-Plvm.compose.reports=true", "-Plvm.compose.metrics=true"];

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Stability {
    Unstable,
    /// Decided at runtime, such as a class with a `List` property: Compose treats it as unstable unless the
    /// instance proves otherwise.
    Runtime,
    Stable,
}

impl Stability {
    fn parse(word: &str) -> Option<Self> {
        match word {
            "stable" => Some(Self::Stable),
            "unstable" => Some(Self::Unstable),
            "runtime" => Some(Self::Runtime),
            _ => None,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::Unstable => "unstable",
            Self::Runtime => "uncertain",
            Self::Stable => "stable",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ClassReport {
    pub name: String,
    pub stability: Stability,
    /// The properties as the report prints them, such as `unstable val stack: Value<ChildStack>`.
    pub fields: Vec<(Option<Stability>, String)>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Composable {
    pub name: String,
    pub restartable: bool,
    pub skippable: bool,
    pub params: Vec<Param>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Param {
    pub stability: Option<Stability>,
    pub name: String,
    pub type_name: String,
}

/// The metrics of one compilation, such as `debug` or `jvm/main`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Metrics {
    pub compilation: String,
    pub composables: u64,
    pub restartable: u64,
    pub skippable: u64,
    pub classes: u64,
    pub unstable_classes: u64,
    pub uncertain_classes: u64,
    pub strong_skipping: bool,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ComposeReport {
    pub classes: Vec<ClassReport>,
    pub composables: Vec<Composable>,
    pub metrics: Vec<Metrics>,
    /// When the newest file was written.
    pub written: Option<SystemTime>,
}

impl ComposeReport {
    pub fn is_empty(&self) -> bool {
        self.classes.is_empty() && self.composables.is_empty() && self.metrics.is_empty()
    }
}

/// What a report says to look at.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Finding {
    /// A class that isn't stable, with its properties that make it so.
    Class { name: String, stability: Stability, fields: Vec<String> },
    /// A restartable composable that can't skip, which happens without strong skipping.
    NotSkippable { name: String },
    /// A composable whose parameters aren't stable: with strong skipping, it recomposes whenever a new instance
    /// arrives, even an equal one.
    UnstableParams { name: String, params: Vec<String> },
}

impl Finding {
    /// The declaration to open: a class or a function, by its qualified name.
    pub fn declaration(&self) -> &str {
        match self {
            Self::Class { name, .. } | Self::NotSkippable { name } | Self::UnstableParams { name, .. } => name,
        }
    }
}

/// The directories of [module]'s reports and metrics: from its `lvmReport` report, else the defaults.
pub fn directories(module: &Module, reported: Option<&crate::project::report::CompilerReports>) -> (PathBuf, PathBuf) {
    let dir = PathBuf::from(&module.dir);
    let reports = reported.and_then(|r| r.compose_reports.clone()).unwrap_or_else(|| REPORTS_DIR.to_owned());
    let metrics = reported.and_then(|r| r.compose_metrics.clone()).unwrap_or_else(|| METRICS_DIR.to_owned());
    (dir.join(reports), dir.join(metrics))
}

/// Reads the reports in [reports] and every compilation's metrics under [metrics].
pub fn load(reports: &Path, metrics: &Path) -> ComposeReport {
    let mut report = ComposeReport::default();
    let mut written = Vec::new();
    for entry in fs::read_dir(reports).into_iter().flatten().filter_map(Result::ok) {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        let Ok(text) = fs::read_to_string(&path) else { continue };
        if name.ends_with("-classes.txt") {
            report.classes.extend(parse_classes(&text));
        } else if name.ends_with("-composables.txt") {
            report.composables.extend(parse_composables(&text));
        } else {
            continue;
        }
        written.extend(entry.metadata().and_then(|m| m.modified()).ok());
    }
    let mut files = Vec::new();
    collect_metrics(metrics, &mut files, 0);
    files.sort();
    for file in files {
        let Ok(text) = fs::read_to_string(&file) else { continue };
        let compilation = file
            .parent()
            .and_then(|dir| dir.strip_prefix(metrics).ok())
            .map(|dir| dir.to_string_lossy().replace('\\', "/"))
            .filter(|dir| !dir.is_empty())
            .unwrap_or_else(|| "main".to_owned());
        if let Some(metrics) = parse_metrics(&compilation, &text) {
            report.metrics.push(metrics);
            written.extend(fs::metadata(&file).and_then(|m| m.modified()).ok());
        }
    }
    report.written = written.into_iter().max();
    report
}

fn collect_metrics(dir: &Path, found: &mut Vec<PathBuf>, depth: usize) {
    for entry in fs::read_dir(dir).into_iter().flatten().filter_map(Result::ok) {
        let path = entry.path();
        if path.is_dir() && depth < 4 {
            collect_metrics(&path, found, depth + 1);
        } else if entry.file_name().to_string_lossy().ends_with("-module.json") {
            found.push(path);
        }
    }
}

/// `*-classes.txt`: `unstable class com.example.Foo {`, its properties, `<runtime stability> = Unstable` and `}`.
pub fn parse_classes(text: &str) -> Vec<ClassReport> {
    let mut classes = Vec::new();
    let mut current: Option<ClassReport> = None;
    for line in text.lines() {
        if let Some(class) = current.as_mut() {
            let trimmed = line.trim();
            if trimmed == "}" {
                classes.extend(current.take());
            } else if !trimmed.starts_with("<runtime stability>") && !trimmed.is_empty() {
                let (word, rest) = trimmed.split_once(' ').unwrap_or((trimmed, ""));
                match Stability::parse(word) {
                    Some(stability) => class.fields.push((Some(stability), rest.to_owned())),
                    None => class.fields.push((None, trimmed.to_owned())),
                }
            }
            continue;
        }
        let Some((word, rest)) = line.split_once(" class ") else { continue };
        let Some(stability) = Stability::parse(word.trim()) else { continue };
        let name = rest.trim_end_matches('{').trim();
        // `Foo<T>` is named `Foo` in its file.
        let name = name.split('<').next().unwrap_or(name).trim().to_owned();
        current = Some(ClassReport { name, stability, fields: Vec::new() });
    }
    classes
}

/// `*-composables.txt`: `restartable skippable scheme("[...]") fun com.example.Foo(`, a parameter on each line and
/// `)`, or one line for a composable without parameters.
pub fn parse_composables(text: &str) -> Vec<Composable> {
    let mut composables = Vec::new();
    let mut open: Option<Composable> = None;
    for line in text.lines() {
        if let Some(composable) = open.as_mut() {
            let trimmed = line.trim();
            if trimmed == ")" {
                composables.extend(open.take());
            } else if !trimmed.is_empty() {
                composable.params.push(parse_param(trimmed));
            }
            continue;
        }
        // The scheme can hold spaces and brackets, so it goes before the words are read.
        let header = match (line.find("scheme(\""), line.find("\")")) {
            (Some(start), Some(end)) if end > start => format!("{}{}", &line[..start], &line[end + 2..]),
            _ => line.to_owned(),
        };
        let Some((modifiers, rest)) = header.split_once("fun ") else { continue };
        let words: Vec<&str> = modifiers.split_whitespace().collect();
        let name = rest.split('(').next().unwrap_or(rest).trim().to_owned();
        let composable = Composable {
            name,
            restartable: words.contains(&"restartable"),
            skippable: words.contains(&"skippable"),
            params: Vec::new(),
        };
        if rest.trim_end().ends_with('(') {
            open = Some(composable);
        } else {
            composables.push(composable);
        }
    }
    composables
}

/// `stable modifier: Modifier? = @static <expression>`, `unused unstable items: List<Item>` or `component: Root`.
fn parse_param(line: &str) -> Param {
    let declaration = line.split(" = ").next().unwrap_or(line);
    let (head, type_name) = declaration.split_once(": ").unwrap_or((declaration, ""));
    let mut words: Vec<&str> = head.split_whitespace().collect();
    let name = words.pop().unwrap_or_default().to_owned();
    let stability = words.iter().find_map(|w| Stability::parse(w));
    Param { stability, name, type_name: type_name.trim().to_owned() }
}

/// `*-module.json` of one compilation.
pub fn parse_metrics(compilation: &str, json: &str) -> Option<Metrics> {
    let value: Value = serde_json::from_str(json).ok()?;
    let number = |key: &str| value[key].as_u64().unwrap_or(0);
    Some(Metrics {
        compilation: compilation.to_owned(),
        composables: number("totalComposables"),
        restartable: number("restartableComposables"),
        skippable: number("skippableComposables"),
        classes: number("totalClasses"),
        unstable_classes: number("inferredUnstableClasses"),
        uncertain_classes: number("inferredUncertainClasses"),
        strong_skipping: value["featureFlags"]["StrongSkipping"].as_bool().unwrap_or(false),
    })
}

/// The classes that aren't stable, then the composables that can't skip or take parameters that aren't stable. A
/// parameter is unstable when the report says so or when its type is a class of the module that isn't stable.
pub fn findings(report: &ComposeReport) -> Vec<Finding> {
    let mut classes: Vec<&ClassReport> = report.classes.iter().filter(|c| c.stability != Stability::Stable).collect();
    classes.sort_by(|a, b| a.stability.cmp(&b.stability).then_with(|| a.name.cmp(&b.name)));
    let unstable_type = |type_name: &str| {
        let simple = type_name.trim_end_matches('?').split('<').next().unwrap_or(type_name).trim();
        report
            .classes
            .iter()
            .find(|c| c.name == simple || c.name.ends_with(&format!(".{simple}")))
            .is_some_and(|c| c.stability != Stability::Stable)
    };
    let mut findings: Vec<Finding> = classes
        .into_iter()
        .map(|class| Finding::Class {
            name: class.name.clone(),
            stability: class.stability,
            fields: class
                .fields
                .iter()
                .filter(|(stability, _)| stability.is_some_and(|s| s != Stability::Stable))
                .map(|(_, field)| field.clone())
                .collect(),
        })
        .collect();
    for composable in &report.composables {
        if composable.restartable && !composable.skippable {
            findings.push(Finding::NotSkippable { name: composable.name.clone() });
        }
        let params: Vec<String> = composable
            .params
            .iter()
            .filter(|p| {
                matches!(p.stability, Some(Stability::Unstable | Stability::Runtime))
                    || (p.stability.is_none() && unstable_type(&p.type_name))
            })
            .map(|p| format!("{}: {}", p.name, p.type_name))
            .collect();
        if !params.is_empty() {
            findings.push(Finding::UnstableParams { name: composable.name.clone(), params });
        }
    }
    findings
}

/// The task that compiles [module] once with the Compose compiler: one compilation, since every compilation of a
/// module writes reports under the same names. The JVM compiles fastest, then Android, then the iOS simulator.
pub fn compile_task(module: &Module) -> Option<String> {
    const PREFERRED: [&str; 6] = [
        "compileKotlinJvm",
        "compileDebugKotlin",
        "compileAndroidMain",
        "compileDebugKotlinAndroid",
        "compileKotlinIosSimulatorArm64",
        "compileKotlin",
    ];
    let name = if module.tasks.is_empty() {
        // No report lists the tasks: the plugins and targets tell which exist.
        let android = ["android.application", "android.library", "android.test"].iter().any(|p| module.has_plugin(p));
        let target = |t: &str| module.targets.iter().any(|x| x == t);
        if android {
            "compileDebugKotlin"
        } else if target("jvm") {
            "compileKotlinJvm"
        } else if target("android") {
            "compileAndroidMain"
        } else if target("ios") {
            "compileKotlinIosSimulatorArm64"
        } else if module.has_plugin("jvm") {
            "compileKotlin"
        } else {
            return None;
        }
    } else {
        PREFERRED.into_iter().find(|task| module.tasks.iter().any(|t| t == task))?
    };
    Some(if module.path == ":" { format!(":{name}") } else { format!("{}:{name}", module.path) })
}

/// The file and line that declare [name], a qualified class or function such as `com.example.Root.Child`, among the
/// Kotlin files of the module in [module_dir].
pub fn locate(module_dir: &Path, name: &str) -> Option<(PathBuf, usize)> {
    let segments: Vec<&str> = name.split('.').collect();
    let mut package_len = segments.iter().take_while(|s| s.starts_with(char::is_lowercase)).count();
    // A function named in lower case, such as a `remember` function, ends the name.
    if package_len == segments.len() {
        package_len -= 1;
    }
    let (package, names) = segments.split_at(package_len);
    let package = package.join(".");
    let mut files = Vec::new();
    kotlin_files(module_dir, &mut files, 0);
    files.sort();
    files.into_iter().find_map(|file| {
        let text = fs::read_to_string(&file).ok()?;
        if file_package(&text) != package {
            return None;
        }
        declaration_line(&text, names).map(|line| (file, line))
    })
}

fn kotlin_files(dir: &Path, found: &mut Vec<PathBuf>, depth: usize) {
    for entry in fs::read_dir(dir).into_iter().flatten().filter_map(Result::ok) {
        let path = entry.path();
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if path.is_dir() {
            if depth < 12 && name != "build" && !name.starts_with('.') {
                kotlin_files(&path, found, depth + 1);
            }
        } else if name.ends_with(".kt") {
            found.push(path);
        }
    }
}

fn file_package(text: &str) -> String {
    text.lines()
        .map(str::trim)
        .find_map(|line| line.strip_prefix("package "))
        .map(|package| package.trim_end_matches(';').replace('`', "").trim().to_owned())
        .unwrap_or_default()
}

/// The 1-based line of the declaration of the last of [names], each declared after the one before it, as a nested
/// class is inside its outer class.
fn declaration_line(text: &str, names: &[&str]) -> Option<usize> {
    let lines: Vec<&str> = text.lines().collect();
    let mut from = 0;
    let mut found = None;
    for name in names {
        let index = (from..lines.len()).find(|&i| declares(lines[i], name))?;
        found = Some(index + 1);
        from = index + 1;
    }
    found
}

fn declares(line: &str, name: &str) -> bool {
    let code = line.split("//").next().unwrap_or(line);
    ["class ", "object ", "interface ", "fun "].iter().any(|keyword| {
        code.match_indices(keyword).any(|(at, _)| {
            let word_start = at == 0 || !code[..at].ends_with(|c: char| c.is_alphanumeric() || c == '_');
            // `fun <T> Foo(` and `fun Scope.Foo(` name the function after the type parameters and the receiver.
            let rest = code[at + keyword.len()..].trim_start();
            let rest = if rest.starts_with('<') { rest.split_once("> ").map_or(rest, |(_, r)| r) } else { rest };
            word_start && declared_name(rest) == name
        })
    })
}

/// The name a declaration starts with: `Cell` of `Scope.Cell(value: T)`, `Box` of `Box<T> {`.
fn declared_name(text: &str) -> &str {
    let token = text.split(['(', ' ', ':', '{', '<']).next().unwrap_or(text);
    token.rsplit('.').next().unwrap_or(token)
}

#[cfg(test)]
mod tests {
    use super::*;

    const CLASSES: &str = "unstable class tech.example.MainActivity {
  <runtime stability> = Unstable
}
stable class tech.example.RootComponent.Config.Details {
  stable val color: String
  <runtime stability> = Stable
}
runtime class tech.example.RootComponent.Child.Colors {
  runtime val colors: List<String>
  stable val onSelect: Function1<String, Unit>
  <runtime stability> = Uncertain(List)
}
unstable class tech.example.RootComponent {
  runtime var $$delegate_0: AppComponentContext
  unstable val stack: Value<ChildStack<Config, Child>>
  <runtime stability> = Unstable
}
stable class tech.example.Box<T> {
  <runtime stability> = Stable
}
";

    const COMPOSABLES: &str = r#"restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun tech.example.RootContent(
  component: RootComponent
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun tech.example.Details(
  child: Details
)
restartable scheme("[androidx.compose.ui.UiComposable]") fun tech.example.Feed(
  unused unstable items: List<Item>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun tech.example.Logo()
"#;

    #[test]
    fn reads_classes_with_their_unstable_properties() {
        let classes = parse_classes(CLASSES);
        let names: Vec<_> = classes.iter().map(|c| (c.name.as_str(), c.stability)).collect();
        assert_eq!(
            names,
            [
                ("tech.example.MainActivity", Stability::Unstable),
                ("tech.example.RootComponent.Config.Details", Stability::Stable),
                ("tech.example.RootComponent.Child.Colors", Stability::Runtime),
                ("tech.example.RootComponent", Stability::Unstable),
                ("tech.example.Box", Stability::Stable),
            ]
        );
        assert_eq!(
            classes[3].fields[1],
            (Some(Stability::Unstable), "val stack: Value<ChildStack<Config, Child>>".into())
        );
    }

    #[test]
    fn reads_composables_and_their_parameters() {
        let composables = parse_composables(COMPOSABLES);
        let names: Vec<_> = composables.iter().map(|c| (c.name.as_str(), c.skippable, c.params.len())).collect();
        assert_eq!(
            names,
            [
                ("tech.example.RootContent", true, 2),
                ("tech.example.Details", true, 1),
                ("tech.example.Feed", false, 1),
                ("tech.example.Logo", true, 0)
            ]
        );
        let modifier = &composables[0].params[1];
        assert_eq!(
            (modifier.stability, modifier.name.as_str(), modifier.type_name.as_str()),
            (Some(Stability::Stable), "modifier", "Modifier?")
        );
        assert_eq!(composables[2].params[0].stability, Some(Stability::Unstable));
    }

    #[test]
    fn finds_unstable_classes_and_what_they_make_recompose() {
        let report = ComposeReport {
            classes: parse_classes(CLASSES),
            composables: parse_composables(COMPOSABLES),
            ..Default::default()
        };
        let found = findings(&report);
        assert_eq!(
            found,
            [
                Finding::Class {
                    name: "tech.example.MainActivity".into(),
                    stability: Stability::Unstable,
                    fields: vec![]
                },
                Finding::Class {
                    name: "tech.example.RootComponent".into(),
                    stability: Stability::Unstable,
                    fields: vec![
                        "var $$delegate_0: AppComponentContext".into(),
                        "val stack: Value<ChildStack<Config, Child>>".into()
                    ],
                },
                Finding::Class {
                    name: "tech.example.RootComponent.Child.Colors".into(),
                    stability: Stability::Runtime,
                    fields: vec!["val colors: List<String>".into()],
                },
                // RootComponent is unstable, though the composable's report doesn't say so.
                Finding::UnstableParams {
                    name: "tech.example.RootContent".into(),
                    params: vec!["component: RootComponent".into()]
                },
                Finding::NotSkippable { name: "tech.example.Feed".into() },
                Finding::UnstableParams { name: "tech.example.Feed".into(), params: vec!["items: List<Item>".into()] },
            ]
        );
    }

    #[test]
    fn reads_the_metrics_of_each_compilation() {
        let dir = tempfile::tempdir().unwrap();
        let metrics = dir.path().join("metrics");
        fs::create_dir_all(metrics.join("jvm/main")).unwrap();
        fs::write(
            metrics.join("jvm/main/shared-module.json"),
            r#"{"totalComposables": 6, "restartableComposables": 6, "skippableComposables": 4, "totalClasses": 7,
                "inferredUnstableClasses": 2, "inferredUncertainClasses": 1, "featureFlags": {"StrongSkipping": true}}"#,
        )
        .unwrap();
        let reports = dir.path().join("reports");
        fs::create_dir_all(&reports).unwrap();
        fs::write(reports.join("shared-classes.txt"), CLASSES).unwrap();
        fs::write(reports.join("shared-composables.csv"), "package,name\n").unwrap();

        let report = load(&reports, &metrics);
        assert_eq!(report.classes.len(), 5);
        assert!(report.composables.is_empty());
        let m = &report.metrics[0];
        assert_eq!(
            (m.compilation.as_str(), m.skippable, m.unstable_classes, m.strong_skipping),
            ("jvm/main", 4, 2, true)
        );
        assert!(report.written.is_some());
        assert!(load(&dir.path().join("none"), &dir.path().join("none")).is_empty());
    }

    #[test]
    fn locates_classes_nested_classes_and_functions() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("src/Root.kt");
        fs::create_dir_all(file.parent().unwrap()).unwrap();
        fs::write(
            &file,
            "package tech.example\n\nclass RootComponent {\n    sealed interface Config {\n        data object Colors : Config\n    }\n    sealed interface Child {\n        class Colors(val colors: List<String>) : Child\n    }\n}\n\n@Composable\nfun RootContent(component: RootComponent) {}\n\n@Composable\nfun <T> RowScope.Cell(value: T) {}\n",
        )
        .unwrap();
        fs::write(dir.path().join("src/Other.kt"), "package tech.other\n\nfun RootContent() {}\n").unwrap();

        assert_eq!(locate(dir.path(), "tech.example.RootComponent"), Some((file.clone(), 3)));
        assert_eq!(locate(dir.path(), "tech.example.RootComponent.Child.Colors"), Some((file.clone(), 8)));
        assert_eq!(locate(dir.path(), "tech.example.RootContent"), Some((file.clone(), 13)));
        assert_eq!(locate(dir.path(), "tech.example.Cell"), Some((file.clone(), 16)));
        assert_eq!(locate(dir.path(), "tech.example.Missing"), None);
    }
}
