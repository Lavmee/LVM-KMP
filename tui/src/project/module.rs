//! What a module's `build.gradle.kts` says: its plugins, its `lvm { }` block, its group and its dependencies.

use std::fs;
use std::ops::Range;
use std::path::{Path, PathBuf};

use serde::Serialize;
use serde_json::Value;

use super::kts::{Form, Kind, Literal, Script, Statement};
use super::lvm_block::{ScriptValue, ValueOp, read_values};

/// LVM's project plugins by the alias that `lvm-versions` gives them after `plugins.`.
pub const BLUEPRINT_ALIASES: &[(&str, &str)] = &[
    ("blueprint.multiplatform", "tech.annexflow.lvm.multiplatform"),
    ("blueprint.compose", "tech.annexflow.lvm.compose"),
    ("blueprint.quality", "tech.annexflow.lvm.quality"),
    ("blueprint.publish", "tech.annexflow.lvm.publish"),
    ("blueprint.main.context", "tech.annexflow.lvm.main-context"),
    ("blueprint.android.application", "tech.annexflow.lvm.android.application"),
    ("blueprint.android.library", "tech.annexflow.lvm.android.library"),
];

const LVM_PLUGIN_PREFIX: &str = "tech.annexflow.lvm.";
const LVM_GROUP: &str = "tech.annexflow.lvm";

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModuleScript {
    pub file: PathBuf,
    /// The script's text, for checks that look for a dependency by name.
    #[serde(skip)]
    pub source: String,
    /// The native `android { namespace = ... }` of AGP, which wins over `lvm { }`.
    pub native_namespace: bool,
    pub plugins: Vec<PluginRef>,
    pub group: Option<String>,
    pub targets: ScriptTargets,
    /// Values that the module's `lvm { }` block sets.
    pub values: Vec<ScriptValue>,
    pub project_dependencies: Vec<Dependency>,
    /// `lvm-*` artifacts the module declares, without the version.
    pub lvm_dependencies: Vec<Dependency>,
}

/// A plugin in `plugins { }`.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct PluginRef {
    /// How the script names it: `id:org.jetbrains.kotlin.multiplatform`, `alias:libs.plugins.detekt`, `kotlin:jvm`.
    pub notation: String,
    /// The plugin ID of an LVM plugin.
    pub lvm: Option<String>,
    /// False for `apply false`, which only puts the plugin on the build classpath.
    pub applied: bool,
    pub line: usize,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScriptTargets {
    pub android: bool,
    pub ios: bool,
    pub jvm: bool,
    pub framework: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Dependency {
    pub configuration: String,
    /// A project path, an `lvm-*` artifact, or a typesafe accessor such as `projects.core` that the caller resolves.
    pub target: String,
    pub line: usize,
}

impl ModuleScript {
    pub fn read(file: &Path) -> Option<Self> {
        let source = fs::read_to_string(file).ok()?;
        Some(Self { file: file.to_owned(), ..Self::parse(&source) })
    }

    pub fn parse(source: &str) -> Self {
        let script = Script::parse(source);
        let mut module = Self { source: source.to_owned(), ..Self::default() };
        for statement in script.statements(script.all()) {
            match (statement.chain.join(".").as_str(), &statement.form) {
                ("plugins", Form::Block { body }) => module.plugins = plugins(&script, body.clone()),
                ("group", Form::Assign { value }) => {
                    if let Some(Literal::Str(group)) = script.literal(value.clone()) {
                        module.group = Some(group);
                    }
                }
                ("android", Form::Block { body }) => {
                    module.native_namespace = script
                        .statements(body.clone())
                        .iter()
                        .any(|s| s.chain == ["namespace"] && matches!(s.form, Form::Assign { .. }));
                }
                ("lvm", Form::Block { body }) => {
                    module.values = read_values(&script, body.clone(), &[]);
                    read_targets(&script, body.clone(), &mut module);
                }
                _ => {}
            }
        }
        read_dependencies(&script, script.all(), None, &mut module);
        module
    }

    pub fn lvm_plugins(&self) -> Vec<String> {
        let mut ids: Vec<String> = self.plugins.iter().filter(|p| p.applied).filter_map(|p| p.lvm.clone()).collect();
        ids.sort();
        ids.dedup();
        ids
    }
}

fn plugins(script: &Script, body: Range<usize>) -> Vec<PluginRef> {
    script
        .statements(body)
        .into_iter()
        .filter_map(|statement| {
            let line = script.line(statement.tokens.start);
            let Form::Call { args, rest, .. } = &statement.form else {
                // `kotlin-dsl` and other backticked plugins
                return (statement.chain.len() == 1 && statement.form == Form::Other).then(|| PluginRef {
                    notation: format!("id:{}", statement.chain[0]),
                    lvm: None,
                    applied: true,
                    line,
                });
            };
            let applied = !rest
                .clone()
                .zip(rest.clone().skip(1))
                .any(|(a, b)| script.text(a) == "apply" && script.text(b) == "false");
            let first = args.first()?;
            match statement.chain.join(".").as_str() {
                "id" => {
                    let id = script.string_args(std::slice::from_ref(first))?.pop()?;
                    let lvm = id.starts_with(LVM_PLUGIN_PREFIX).then(|| id.clone());
                    Some(PluginRef { notation: format!("id:{id}"), lvm, applied, line })
                }
                "alias" => {
                    let chain = script.chain_at(first.start);
                    let after_plugins =
                        chain.iter().position(|part| part == "plugins").map(|i| chain[i + 1..].join("."));
                    let lvm = after_plugins.and_then(|alias| {
                        BLUEPRINT_ALIASES.iter().find(|(name, _)| *name == alias).map(|(_, id)| (*id).to_owned())
                    });
                    Some(PluginRef { notation: format!("alias:{}", chain.join(".")), lvm, applied, line })
                }
                "kotlin" => {
                    let name = script.string_args(std::slice::from_ref(first))?.pop()?;
                    Some(PluginRef { notation: format!("kotlin:{name}"), lvm: None, applied, line })
                }
                _ => None,
            }
        })
        .collect()
}

/// `targets { android(); ios { framework { baseName = "Kit" } }; jvm() }`, and the values set inside.
fn read_targets(script: &Script, lvm: Range<usize>, module: &mut ModuleScript) {
    for target in script.block_statements(lvm, "targets") {
        let body = match &target.form {
            Form::Block { body } => Some(body.clone()),
            Form::Call { lambda, .. } => lambda.clone(),
            _ => continue,
        };
        let name = target.chain.join(".");
        match name.as_str() {
            "android" => module.targets.android = true,
            "ios" => module.targets.ios = true,
            "jvm" => module.targets.jvm = true,
            _ => continue,
        }
        let Some(body) = body else { continue };
        for statement in script.statements(body.clone()) {
            target_value(script, &name, &statement, module);
        }
        if name == "ios" {
            for setting in script.block_statements(body, "framework") {
                if let (["baseName"], Form::Assign { value }) = (statement_chain(&setting).as_slice(), &setting.form)
                    && let Some(Literal::Str(base_name)) = script.literal(value.clone())
                {
                    module.targets.framework = Some(base_name);
                }
            }
        }
    }
}

fn statement_chain(statement: &Statement) -> Vec<&str> {
    statement.chain.iter().map(String::as_str).collect()
}

/// `hostTests = false` in `android { }` and `architectures.set(...)` in `ios { }` are build-wide keys of their own.
fn target_value(script: &Script, target: &str, statement: &Statement, module: &mut ModuleScript) {
    let key = match (target, statement_chain(statement).as_slice()) {
        ("android", ["hostTests"]) => "android.hostTests",
        ("ios", ["architectures"] | ["architectures", "set"]) => "ios.architectures",
        _ => return,
    };
    let value = match &statement.form {
        Form::Assign { value } => script.literal(value.clone()),
        Form::Call { args, .. } if args.len() == 1 => script.literal(args[0].clone()),
        _ => None,
    };
    if let Some(literal) = value {
        let line = script.line(statement.tokens.start);
        module.values.push(ScriptValue {
            key: key.to_owned(),
            op: ValueOp::Set(super::lvm_block::json(literal)),
            line,
        });
    }
}

/// Dependencies in `dependencies { }` blocks, and in those of Kotlin source sets such as
/// `sourceSets.commonMain.dependencies { }`, whose configurations are named after the source set.
fn read_dependencies(script: &Script, body: Range<usize>, source_set: Option<&str>, module: &mut ModuleScript) {
    for statement in script.statements(body) {
        let inner = match &statement.form {
            Form::Block { body } => body.clone(),
            Form::Call { lambda: Some(body), .. } => body.clone(),
            _ => continue,
        };
        if statement.chain.last().is_some_and(|last| last == "dependencies") {
            let set = statement.chain.iter().rev().nth(1).map(String::as_str).filter(|s| *s != "sourceSets");
            let set = set.or(source_set);
            for declaration in script.statements(inner) {
                let Form::Call { args, .. } = &declaration.form else {
                    continue;
                };
                if declaration.chain.len() != 1 || args.is_empty() {
                    continue;
                }
                let configuration = match set {
                    Some(set) => format!("{set}{}", capitalize(&declaration.chain[0])),
                    None => declaration.chain[0].clone(),
                };
                let line = script.line(declaration.tokens.start);
                for arg in args {
                    match notation(script, arg.clone()) {
                        Some(Notation::Project(target)) => module.project_dependencies.push(Dependency {
                            configuration: configuration.clone(),
                            target,
                            line,
                        }),
                        Some(Notation::Lvm(target)) => module.lvm_dependencies.push(Dependency {
                            configuration: configuration.clone(),
                            target,
                            line,
                        }),
                        None => {}
                    }
                }
            }
        } else {
            // `sourceSets { commonMain { dependencies { } } }` or `commonMain.dependencies { }` inside `kotlin { }`.
            let set = statement.chain.last().map(String::as_str).filter(|name| is_source_set(name));
            read_dependencies(script, inner, set.or(source_set), module);
        }
    }
}

fn is_source_set(name: &str) -> bool {
    name.ends_with("Main") || name.ends_with("Test")
}

fn capitalize(name: &str) -> String {
    let mut chars = name.chars();
    chars.next().map(|first| first.to_uppercase().chain(chars).collect()).unwrap_or_default()
}

enum Notation {
    Project(String),
    Lvm(String),
}

/// What a dependency notation points at, where it is a project or an `lvm-*` artifact.
fn notation(script: &Script, arg: Range<usize>) -> Option<Notation> {
    let first = arg.start;
    match &script.tokens.get(first)?.kind {
        Kind::Str(Some(coordinates)) => {
            let mut parts = coordinates.split(':');
            (parts.next() == Some(LVM_GROUP))
                .then(|| parts.next().map(|artifact| Notation::Lvm(artifact.to_owned())))?
        }
        Kind::Ident => {
            let chain = script.chain_at(first);
            let call_args = |name: &str| {
                script.calls(arg.clone(), name).into_iter().find(|(at, _)| *at == first).map(|(_, args)| args)
            };
            match chain.as_slice() {
                [call] if matches!(call.as_str(), "platform" | "enforcedPlatform" | "testFixtures" | "kotlin") => {
                    let args = call_args(call)?;
                    notation(script, args.first()?.clone())
                }
                [call] if call == "project" => {
                    let path = script.string_args(&call_args("project")?)?.pop()?;
                    Some(Notation::Project(path))
                }
                [projects, rest @ ..] if projects == "projects" && !rest.is_empty() => {
                    Some(Notation::Project(format!("projects.{}", rest.join("."))))
                }
                // A catalog accessor such as lvmLibs.lvm.decompose.compose -> lvm-decompose-compose.
                [_, lvm, rest @ ..] if lvm == "lvm" && !rest.is_empty() => {
                    Some(Notation::Lvm(format!("lvm-{}", rest.join("-"))))
                }
                _ => None,
            }
        }
        _ => None,
    }
}

/// Whether [dir] uses the flat source layout, the standard one, both, or neither that lvm-tui can see.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Layout {
    Flat,
    Standard,
    Mixed,
    Unknown,
}

impl Layout {
    pub fn detect(dir: &Path) -> Self {
        let entries: Vec<(String, PathBuf)> = fs::read_dir(dir)
            .map(|entries| {
                entries
                    .filter_map(Result::ok)
                    .map(|e| (e.file_name().to_string_lossy().into_owned(), e.path()))
                    .collect()
            })
            .unwrap_or_default();
        let src = dir.join("src");
        let flat_directories = entries.iter().any(|(name, path)| {
            path.is_dir()
                && (name.starts_with("src@") || name.starts_with("test@") || (name == "test" && has_kotlin_files(path)))
        });
        let flat = flat_directories || has_kotlin_files(&src);
        let standard = fs::read_dir(&src)
            .map(|sets| {
                sets.filter_map(Result::ok)
                    .any(|set| set.path().join("kotlin").is_dir() || set.path().join("java").is_dir())
            })
            .unwrap_or(false);
        match (flat, standard) {
            (true, true) => Self::Mixed,
            (true, false) => Self::Flat,
            (false, true) => Self::Standard,
            (false, false) => Self::Unknown,
        }
    }
}

/// Kotlin files directly in [dir] or in its package directories, but not in source set directories such as
/// `src/main`, whose files belong to the standard layout.
fn has_kotlin_files(dir: &Path) -> bool {
    fn visit(dir: &Path, depth: usize) -> bool {
        let Ok(entries) = fs::read_dir(dir) else { return false };
        entries.filter_map(Result::ok).any(|entry| {
            let path = entry.path();
            if path.is_dir() {
                let name = entry.file_name().to_string_lossy().into_owned();
                let source_set = depth == 0
                    && (name == "main"
                        || name == "test"
                        || name.ends_with("Main")
                        || name.ends_with("Test")
                        || name == "androidTest");
                !source_set && depth < 8 && visit(&path, depth + 1)
            } else {
                path.extension().is_some_and(|ext| ext == "kt" || ext == "kts")
            }
        })
    }
    visit(dir, 0)
}

/// The value a module's script gives [key], applying its writes in order to [base].
pub fn apply_writes(values: &[ScriptValue], key: &str, base: Option<Value>) -> Option<Value> {
    values.iter().filter(|v| v.key == key).fold(base, |current, write| match &write.op {
        ValueOp::Set(value) => Some(value.clone()),
        ValueOp::Add(items) => {
            let mut list = match current {
                Some(Value::Array(list)) => list,
                _ => Vec::new(),
            };
            list.extend(items.iter().cloned().map(Value::String));
            Some(Value::Array(list))
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const MODULE: &str = r#"
plugins {
    alias(lvmCatalog.plugins.blueprint.multiplatform)
    alias(lvmCatalog.plugins.blueprint.main.context)
    id("tech.annexflow.lvm.quality")
    alias(libs.plugins.kotlin.serialization)
    kotlin("plugin.parcelize")
}

group = "com.example"

lvm {
    targets {
        android { hostTests = false }
        ios {
            architectures.set(listOf("iosSimulatorArm64"))
            framework { baseName = "SharedKit"; isStatic = true }
        }
        jvm()
    }
    kotlin { optIns.add("kotlin.uuid.ExperimentalUuidApi") }
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core"))
        api(projects.feature.chatHistory)
        implementation(lvmCatalog.lvm.decompose.compose)
        implementation(platform("tech.annexflow.lvm:lvm-bom:0.3.2"))
    }
    sourceSets {
        jvmTest {
            dependencies { implementation(libs.junit) }
        }
    }
}

dependencies {
    detektPlugins(project(":detekt-rules"))
}
"#;

    #[test]
    fn reads_plugins_by_alias_and_id() {
        let module = ModuleScript::parse(MODULE);
        assert_eq!(
            module.lvm_plugins(),
            ["tech.annexflow.lvm.main-context", "tech.annexflow.lvm.multiplatform", "tech.annexflow.lvm.quality"]
        );
        let notations: Vec<_> = module.plugins.iter().map(|p| p.notation.as_str()).collect();
        assert!(notations.contains(&"alias:libs.plugins.kotlin.serialization"));
        assert!(notations.contains(&"kotlin:plugin.parcelize"));
        assert_eq!(module.group.as_deref(), Some("com.example"));
    }

    #[test]
    fn a_plugin_with_apply_false_is_not_applied() {
        let module =
            ModuleScript::parse("plugins {\n    alias(lvmLibs.plugins.blueprint.multiplatform) apply false\n}\n");
        assert_eq!(module.plugins.len(), 1);
        assert!(module.lvm_plugins().is_empty());
    }

    #[test]
    fn reads_targets_and_their_values() {
        let module = ModuleScript::parse(MODULE);
        assert_eq!(
            module.targets,
            ScriptTargets { android: true, ios: true, jvm: true, framework: Some("SharedKit".into()) }
        );
        let keys: Vec<_> = module.values.iter().map(|v| v.key.as_str()).collect();
        assert_eq!(keys, ["kotlin.optIns", "android.hostTests", "ios.architectures"]);
    }

    #[test]
    fn reads_dependencies_with_source_set_configurations() {
        let module = ModuleScript::parse(MODULE);
        let projects: Vec<_> =
            module.project_dependencies.iter().map(|d| (d.configuration.as_str(), d.target.as_str())).collect();
        assert_eq!(
            projects,
            [
                ("commonMainImplementation", ":core"),
                ("commonMainApi", "projects.feature.chatHistory"),
                ("detektPlugins", ":detekt-rules"),
            ]
        );
        let lvm: Vec<_> =
            module.lvm_dependencies.iter().map(|d| (d.configuration.as_str(), d.target.as_str())).collect();
        assert_eq!(
            lvm,
            [("commonMainImplementation", "lvm-decompose-compose"), ("commonMainImplementation", "lvm-bom")]
        );
    }

    #[test]
    fn detects_the_source_layout() {
        let dir = tempfile::tempdir().unwrap();
        let module = dir.path();
        assert_eq!(Layout::detect(module), Layout::Unknown);
        fs::create_dir_all(module.join("src/main/res")).unwrap();
        fs::write(module.join("src/Greeting.kt"), "").unwrap();
        assert_eq!(Layout::detect(module), Layout::Flat);
        fs::create_dir_all(module.join("src/commonMain/kotlin")).unwrap();
        assert_eq!(Layout::detect(module), Layout::Mixed);
        fs::remove_file(module.join("src/Greeting.kt")).unwrap();
        assert_eq!(Layout::detect(module), Layout::Standard);
    }
}
