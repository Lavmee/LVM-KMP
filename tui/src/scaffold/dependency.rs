//! Adds one of LVM's libraries to a module, with what the library needs from the module.

use std::collections::BTreeSet;
use std::path::Path;

use anyhow::{Result, bail};

use super::Conventions;
use crate::doctor;
use crate::edit::ChangeSet;
use crate::project::Project;
use crate::project::kts_edit;
use crate::project::model::Module;

/// LVM's libraries in the release lvm-tui ships with.
pub fn libraries() -> Vec<&'static str> {
    env!("LVM_LIBRARIES").split(',').filter(|name| !name.is_empty()).collect()
}

/// A library to add: to a source set such as `commonMain` with a scope such as `implementation`, or to a configuration
/// such as `testImplementation` in a module without Kotlin Multiplatform.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DependencySpec {
    pub artifact: String,
    pub source_set: Option<String>,
    pub configuration: String,
}

/// Where [module] takes dependencies: its source sets, or the configurations of an Android module.
pub fn places(module: &Module) -> Vec<String> {
    if !module.has_plugin("multiplatform") {
        return ["implementation", "api", "testImplementation", "androidTestImplementation", "debugImplementation"]
            .map(str::to_owned)
            .to_vec();
    }
    let mut sets: Vec<String> =
        module.source_sets.iter().filter(|s| s.ends_with("Main") || s.ends_with("Test")).cloned().collect();
    if sets.is_empty() {
        sets.push("commonMain".into());
        sets.push("commonTest".into());
        sets.extend(module.targets.iter().map(|target| format!("{target}Main")));
    }
    // commonMain first, the rest as they come.
    sets.sort_by_key(|set| (set != "commonMain", set != "commonTest", set.clone()));
    sets
}

/// The change that adds [spec] to [module], and notes on what else it changes.
pub fn add(project: &Project, module: &Module, spec: &DependencySpec) -> Result<(ChangeSet, Vec<String>)> {
    if module.lvm_dependencies.iter().any(|d| d.target == spec.artifact) {
        bail!("{} depends on {} already", module.path, spec.artifact);
    }
    let Some(build_file) = &module.build_file else { bail!("{} has no build script", module.path) };
    let root = &project.root;
    let file = build_file.strip_prefix(root).unwrap_or(build_file).to_owned();
    let conventions = Conventions::of(project);
    let mut changes = ChangeSet::default();
    let mut notes = Vec::new();
    let mut plugins: Vec<String> = module.plugins.clone();
    let android_target = module.targets.iter().any(|t| t == "android");

    if spec.artifact == "lvm-main-context" {
        // The plugin adds the annotation and runs the compiler plugin; the library alone does nothing.
        add_plugin(&mut changes, root, &file, &conventions.plugin("main-context"));
        plugins.push("tech.annexflow.lvm.main-context".into());
        notes.push("lvm-main-context comes with tech.annexflow.lvm.main-context, which also runs its compiler plugin, so the module gets the plugin instead.".into());
    } else {
        let notation = match &conventions.catalog {
            Some(catalog) => format!("{catalog}.lvm.{}", spec.artifact.trim_start_matches("lvm-").replace('-', ".")),
            None => format!("\"tech.annexflow.lvm:{}\"", spec.artifact),
        };
        let blocks = dependency_blocks(&changes.current(root, &file), spec);
        let blocks: Vec<&str> = blocks.iter().map(String::as_str).collect();
        let mut statements = vec![format!("{}({notation})", spec.configuration)];
        if conventions.catalog.is_none() && !changes.current(root, &file).contains("lvm-bom") {
            let version = crate::upgrade::current_version(project).unwrap_or_else(|| crate::LVM_VERSION.to_owned());
            statements.insert(0, format!("{}(platform(\"tech.annexflow.lvm:lvm-bom:{version}\"))", spec.configuration));
            notes.push("Without the lvmLibs catalog, lvm-bom gives LVM's libraries their versions.".into());
        }
        for statement in statements {
            let source = changes.current(root, &file);
            changes.edit(root, &file, &[kts_edit::add_statement(&source, &blocks, &statement)]);
        }
    }

    match spec.artifact.as_str() {
        "lvm-storage" if !has_serialization(module) => {
            let line = match &conventions.catalog {
                Some(catalog) => format!("    alias({catalog}.plugins.kotlin.serialization)"),
                None => "    id(\"org.jetbrains.kotlin.plugin.serialization\")".to_owned(),
            };
            add_plugin(&mut changes, root, &file, &line);
            declare_in_root(project, &mut changes, &BTreeSet::from(["kotlin.serialization"]));
            notes.push(
                "lvm-storage stores @Serializable values, so the module gets the Kotlin serialization plugin.".into(),
            );
        }
        "lvm-decompose-compose" if !module.has_plugin("compose") => {
            add_plugin(&mut changes, root, &file, &conventions.plugin("compose"));
            plugins.push("tech.annexflow.lvm.compose".into());
            notes.push("lvm-decompose-compose is Compose code, so the module gets tech.annexflow.lvm.compose.".into());
        }
        "lvm-decompose-permissions"
            if module.targets.iter().any(|t| t == "jvm") && spec.source_set.as_deref() == Some("commonMain") =>
        {
            notes.push("lvm-decompose-permissions is for Android and iOS only, and the module has a JVM target: put it in androidMain and iosMain instead.".into());
        }
        _ => {}
    }
    declare_in_root(project, &mut changes, &doctor::required_aliases(&plugins, android_target));
    Ok((changes, notes))
}

/// The blocks for [spec]: a source set's `dependencies { }` in `kotlin { }`, in the form the script uses already, or
/// the top-level `dependencies { }`.
fn dependency_blocks(source: &str, spec: &DependencySpec) -> Vec<String> {
    let Some(set) = &spec.source_set else { return vec!["dependencies".into()] };
    let forms = [
        vec!["kotlin".to_owned(), format!("sourceSets.{set}.dependencies")],
        vec!["kotlin".to_owned(), "sourceSets".to_owned(), format!("{set}.dependencies")],
        vec!["kotlin".to_owned(), "sourceSets".to_owned(), set.clone(), "dependencies".to_owned()],
    ];
    forms
        .iter()
        .find(|blocks| kts_edit::has_blocks(source, &blocks.iter().map(String::as_str).collect::<Vec<_>>()))
        .unwrap_or(&forms[0])
        .clone()
}

fn add_plugin(changes: &mut ChangeSet, root: &Path, file: &Path, line: &str) {
    let source = changes.current(root, file);
    if source.contains(line.trim()) {
        return;
    }
    changes.edit(root, file, &[kts_edit::add_statement(&source, &["plugins"], line.trim())]);
}

fn declare_in_root(project: &Project, changes: &mut ChangeSet, aliases: &BTreeSet<&str>) {
    let file = Path::new("build.gradle.kts");
    for line in doctor::missing_root_lines(project, aliases) {
        let source = changes.current(&project.root, file);
        if !source.contains(&line) {
            changes.edit(&project.root, file, &[kts_edit::add_statement(&source, &["plugins"], &line)]);
        }
    }
}

fn has_serialization(module: &Module) -> bool {
    module.script.as_ref().is_some_and(|s| s.plugins.iter().any(|p| p.notation.contains("serialization")))
}
