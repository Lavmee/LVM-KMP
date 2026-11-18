//! The forms that create modules, builds and dependencies, and the specs they make.

use std::collections::BTreeMap;
use std::path::PathBuf;

use anyhow::{Result, bail};

use crate::form::{Field, Form};
use crate::project::Project;
use crate::project::model::Module;
use crate::scaffold::dependency::{self, DependencySpec};
use crate::scaffold::{ModuleKind, ModuleSpec, ProjectSpec};

const TARGETS: [&str; 3] = ["android", "ios", "jvm"];
const PLUGINS: [&str; 4] = ["compose", "quality", "main-context", "publish"];

/// The group most modules of [project] use, else `com.example`.
fn usual_group(project: &Project) -> String {
    let mut groups: BTreeMap<String, usize> = BTreeMap::new();
    for module in &project.modules {
        let group = module
            .script
            .as_ref()
            .and_then(|s| s.group.clone())
            .or_else(|| module.publication.as_ref().map(|p| p.group.clone()));
        if let Some(group) = group {
            *groups.entry(group).or_default() += 1;
        }
    }
    groups
        .into_iter()
        .max_by_key(|(_, count)| *count)
        .map(|(group, _)| group)
        .unwrap_or_else(|| "com.example".to_owned())
}

pub fn module_form(project: &Project) -> Form {
    let quality = project.modules.iter().any(|m| m.has_plugin("quality"));
    let plugins: Vec<(&str, bool)> = PLUGINS.iter().map(|p| (*p, *p == "quality" && quality)).collect();
    Form::new(
        "New module",
        vec![
            Field::text("path", "Path", ":", "a project path such as :feature:chat"),
            Field::choice("kind", "Kind", ModuleKind::ALL.iter().map(|k| k.label().to_owned()).collect(), 0)
                .with_hint("left and right choose"),
            Field::toggles("targets", "Targets", &TARGETS.map(|t| (t, true))).with_hint("space turns one on or off"),
            Field::toggles("plugins", "LVM plugins", &plugins).with_hint("space turns one on or off"),
            Field::text("group", "Group", usual_group(project), "the module's group, and the start of its namespace"),
            Field::text("namespace", "Namespace", "", "empty: the group followed by the path"),
        ],
    )
}

/// Shows the targets only for a Kotlin Multiplatform library.
pub fn sync_module_form(form: &mut Form) {
    let kmp = ModuleKind::ALL[form.choice("kind")] == ModuleKind::Multiplatform;
    form.set_visible("targets", kmp);
}

pub fn module_spec(form: &Form) -> ModuleSpec {
    let kind = ModuleKind::ALL[form.choice("kind")];
    let namespace = Some(form.text("namespace")).filter(|n| !n.is_empty());
    ModuleSpec {
        path: form.text("path"),
        kind,
        targets: if kind == ModuleKind::Multiplatform { form.toggled("targets") } else { Vec::new() },
        plugins: form.toggled("plugins"),
        group: form.text("group"),
        namespace,
        framework: None,
        dependencies: Vec::new(),
    }
}

pub fn dependency_form(module: &Module) -> Form {
    let declared: Vec<&str> = module.lvm_dependencies.iter().map(|d| d.target.as_str()).collect();
    let libraries = dependency::libraries().into_iter().filter(|l| !declared.contains(l)).map(str::to_owned).collect();
    let mut fields = vec![
        Field::pick("library", "Library", libraries).with_hint("type to filter, up and down choose"),
        Field::choice(
            "place",
            if module.has_plugin("multiplatform") { "Source set" } else { "Configuration" },
            dependency::places(module),
            0,
        )
        .with_hint("left and right choose"),
    ];
    if module.has_plugin("multiplatform") {
        fields.push(
            Field::choice("scope", "Scope", vec!["implementation".into(), "api".into()], 0)
                .with_hint("api also gives it to modules that depend on this one"),
        );
    }
    Form::new(format!("Add an LVM library to {}", module.path), fields)
}

pub fn dependency_spec(form: &Form, module: &Module) -> Result<DependencySpec> {
    let Some(artifact) = form.picked("library") else { bail!("no library matches the filter") };
    let place = form.chosen("place");
    Ok(if module.has_plugin("multiplatform") {
        DependencySpec { artifact, source_set: Some(place), configuration: form.chosen("scope") }
    } else {
        DependencySpec { artifact, source_set: None, configuration: place }
    })
}

/// What `lvm-tui new` starts with, from its flags.
#[derive(Debug, Clone)]
pub struct ProjectDefaults {
    pub dir: PathBuf,
    pub name: String,
    pub group: String,
    pub lvm_version: String,
    pub targets: Vec<String>,
    pub app: bool,
    pub compose: bool,
    pub quality: bool,
    pub flat: bool,
    pub maven_local: bool,
}

impl ProjectDefaults {
    pub fn spec(&self) -> ProjectSpec {
        let plugins = [("compose", self.compose), ("quality", self.quality)]
            .into_iter()
            .filter(|(_, on)| *on)
            .map(|(p, _)| p.to_owned())
            .collect();
        let mut spec =
            ProjectSpec::standard(&self.name, &self.group, &self.lvm_version, self.targets.clone(), self.app, plugins);
        spec.flat = self.flat;
        spec.maven_local = self.maven_local;
        spec
    }
}

pub fn project_form(defaults: &ProjectDefaults) -> Form {
    let has = |target: &str| defaults.targets.iter().any(|t| t == target);
    Form::new(
        "New build with LVM",
        vec![
            Field::text(
                "dir",
                "Directory",
                defaults.dir.display().to_string(),
                "where the build goes; created when missing",
            ),
            Field::text("name", "Name", defaults.name.clone(), "rootProject.name"),
            Field::text("group", "Group", defaults.group.clone(), "the group and package of the modules"),
            Field::text(
                "lvm",
                "LVM version",
                defaults.lvm_version.clone(),
                "the latest release when Maven Central answered",
            ),
            Field::toggles("targets", "Shared targets", &TARGETS.map(|t| (t, has(t))))
                .with_hint("the targets of :shared"),
            Field::toggles(
                "options",
                "Options",
                &[
                    ("Android app", defaults.app),
                    ("compose", defaults.compose),
                    ("quality", defaults.quality),
                    ("flat layout", defaults.flat),
                    ("Maven local", defaults.maven_local),
                ],
            )
            .with_hint("space turns one on or off; Maven local resolves LVM from ~/.m2 first"),
        ],
    )
}

pub fn project_defaults(form: &Form) -> ProjectDefaults {
    let options = form.toggled("options");
    let on = |name: &str| options.iter().any(|o| o == name);
    ProjectDefaults {
        dir: PathBuf::from(form.text("dir")),
        name: form.text("name"),
        group: form.text("group"),
        lvm_version: form.text("lvm"),
        targets: form.toggled("targets"),
        app: on("Android app"),
        compose: on("compose"),
        quality: on("quality"),
        flat: on("flat layout"),
        maven_local: on("Maven local"),
    }
}
