//! New modules and new builds, from the templates next to this file, written the way LVM's README sets them up.

pub mod dependency;

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

use anyhow::{Result, bail};

use crate::doctor;
use crate::edit::{ChangeSet, TextEdit};
use crate::project::Project;
use crate::project::kts::{Form, Script};
use crate::project::module::BLUEPRINT_ALIASES;

const MULTIPLATFORM: &str = include_str!("templates/multiplatform.gradle.kts");
const ANDROID_APPLICATION: &str = include_str!("templates/android-application.gradle.kts");
const ANDROID_LIBRARY: &str = include_str!("templates/android-library.gradle.kts");
const SETTINGS: &str = include_str!("templates/settings.gradle.kts");
const ROOT: &str = include_str!("templates/root.gradle.kts");
const GRADLE_PROPERTIES: &str = include_str!("templates/gradle.properties");
const GITIGNORE: &str = include_str!("templates/gitignore");

// The wrapper of LVM's own build, which uses the Gradle version that LVM is tested with.
const WRAPPER_JAR: &[u8] = include_bytes!("../../../gradle/wrapper/gradle-wrapper.jar");
const WRAPPER_PROPERTIES: &str = include_str!("../../../gradle/wrapper/gradle-wrapper.properties");
const GRADLEW: &str = include_str!("../../../gradlew");
const GRADLEW_BAT: &str = include_str!("../../../gradlew.bat");

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModuleKind {
    Multiplatform,
    AndroidApplication,
    AndroidLibrary,
}

impl ModuleKind {
    pub const ALL: [ModuleKind; 3] =
        [ModuleKind::Multiplatform, ModuleKind::AndroidApplication, ModuleKind::AndroidLibrary];

    pub fn label(self) -> &'static str {
        match self {
            Self::Multiplatform => "KMP library",
            Self::AndroidApplication => "Android app",
            Self::AndroidLibrary => "Android library",
        }
    }

    fn plugin(self) -> &'static str {
        match self {
            Self::Multiplatform => "multiplatform",
            Self::AndroidApplication => "android.application",
            Self::AndroidLibrary => "android.library",
        }
    }
}

/// A module to create.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModuleSpec {
    pub path: String,
    pub kind: ModuleKind,
    /// `android`, `ios` and `jvm`, for a Kotlin Multiplatform library.
    pub targets: Vec<String>,
    /// LVM's plugins besides the kind's own: `compose`, `quality`, `main-context`, `publish`.
    pub plugins: Vec<String>,
    pub group: String,
    /// The Android namespace; the group followed by the path when it is empty.
    pub namespace: Option<String>,
    /// The base name of an iOS framework, for a library that an iOS app links.
    pub framework: Option<String>,
    /// Modules it depends on, such as `:shared`.
    pub dependencies: Vec<String>,
}

/// How the build writes its scripts: the catalog its plugins come from, and its source layout.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Conventions {
    /// The name of the `lvm-versions` catalog, or `None` where the settings turn it off.
    pub catalog: Option<String>,
    pub flat: bool,
}

impl Conventions {
    pub fn of(project: &Project) -> Self {
        let flat =
            project.properties.get("lvm.kotlin.flatLayout").map(|v| v.trim() == "true").or_else(|| {
                project.settings.as_ref().and_then(|s| {
                    s.values.iter().rev().find(|v| v.key == "kotlin.flatLayout").map(|v| {
                        matches!(&v.op, crate::project::lvm_block::ValueOp::Set(serde_json::Value::Bool(true)))
                    })
                })
            });
        Self {
            catalog: (project.options.catalog != Some(false)).then(|| project.options.catalog_name.clone()),
            flat: flat.unwrap_or(false),
        }
    }

    fn plugin(&self, suffix: &str) -> String {
        let id = format!("tech.annexflow.lvm.{suffix}");
        match (&self.catalog, BLUEPRINT_ALIASES.iter().find(|(_, plugin)| *plugin == id)) {
            (Some(catalog), Some((alias, _))) => format!("    alias({catalog}.plugins.{alias})"),
            _ => format!("    id(\"{id}\")"),
        }
    }
}

impl ModuleSpec {
    /// The module's LVM plugins, by ID.
    pub fn lvm_plugins(&self) -> Vec<String> {
        std::iter::once(self.kind.plugin())
            .chain(self.plugins.iter().map(String::as_str))
            .map(|suffix| format!("tech.annexflow.lvm.{suffix}"))
            .collect()
    }

    pub fn dir(&self) -> PathBuf {
        self.path.trim_start_matches(':').split(':').collect()
    }

    fn has(&self, plugin: &str) -> bool {
        self.plugins.iter().any(|p| p == plugin)
    }

    fn android(&self) -> bool {
        self.kind != ModuleKind::Multiplatform || self.targets.iter().any(|t| t == "android")
    }

    /// The Android namespace, or the package of a module without Android: the group followed by the path.
    pub fn package(&self) -> String {
        if let Some(namespace) = self.namespace.as_ref().filter(|n| !n.is_empty()) {
            return namespace.clone();
        }
        let segments = self.path.split(':').filter(|s| !s.is_empty()).map(|s| s.replace('-', "_").to_lowercase());
        std::iter::once(self.group.clone()).chain(segments).collect::<Vec<_>>().join(".")
    }

    /// `:feature:chat-history` -> `ChatHistory`.
    fn type_name(&self) -> String {
        let last = self.path.rsplit(':').next().unwrap_or("Module");
        let name: String = last
            .split(['-', '_'])
            .map(|part| {
                let mut chars = part.chars();
                chars.next().map(|c| c.to_uppercase().chain(chars).collect::<String>()).unwrap_or_default()
            })
            .collect();
        if name.starts_with(char::is_alphabetic) { name } else { format!("Module{name}") }
    }

    fn validate(&self, project: Option<&Project>) -> Result<()> {
        let valid = |segment: &str| {
            !segment.is_empty() && segment.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
        };
        if !self.path.starts_with(':') || !self.path[1..].split(':').all(valid) {
            bail!("{:?} is not a project path such as :feature:chat", self.path);
        }
        if let Some(project) = project
            && project.module(&self.path).is_some()
        {
            bail!("the build already has {}", self.path);
        }
        if self.kind == ModuleKind::Multiplatform && self.targets.is_empty() {
            bail!("a Kotlin Multiplatform library needs a target: android, ios or jvm");
        }
        let group_ok = !self.group.is_empty() && self.group.split('.').all(valid);
        if self.android() && !group_ok && self.namespace.as_ref().is_none_or(String::is_empty) {
            bail!("Android needs a group or a namespace, such as com.example");
        }
        Ok(())
    }
}

/// The files of a new module, by path relative to the build root.
pub fn module_files(spec: &ModuleSpec, conventions: &Conventions) -> Vec<(PathBuf, String)> {
    let dir = spec.dir();
    let mut plugins: Vec<String> = std::iter::once(spec.kind.plugin()).map(|p| conventions.plugin(p)).collect();
    plugins.extend(spec.plugins.iter().map(|p| conventions.plugin(p)));
    let compose_runtime =
        conventions.catalog.as_ref().filter(|_| spec.has("compose")).map(|c| format!("{c}.compose.runtime"));
    let mut dependencies: Vec<String> = spec.dependencies.iter().map(|path| format!("project(\"{path}\")")).collect();
    dependencies.extend(compose_runtime);

    let script = match spec.kind {
        ModuleKind::Multiplatform => {
            let targets: Vec<String> = spec
                .targets
                .iter()
                .map(|target| match (target.as_str(), &spec.framework) {
                    ("ios", Some(framework)) => {
                        format!("        ios {{ framework {{ baseName = \"{framework}\"; isStatic = true }} }}")
                    }
                    (target, _) => format!("        {target}()"),
                })
                .collect();
            let kotlin = if dependencies.is_empty() {
                String::new()
            } else {
                let lines: Vec<String> = dependencies.iter().map(|d| format!("        implementation({d})")).collect();
                format!("\nkotlin {{\n    sourceSets.commonMain.dependencies {{\n{}\n    }}\n}}\n", lines.join("\n"))
            };
            fill(
                MULTIPLATFORM,
                &[
                    ("plugins", plugins.join("\n")),
                    ("group", spec.group.clone()),
                    ("targets", targets.join("\n")),
                    ("kotlin", kotlin),
                ],
            )
        }
        ModuleKind::AndroidApplication | ModuleKind::AndroidLibrary => {
            let template =
                if spec.kind == ModuleKind::AndroidApplication { ANDROID_APPLICATION } else { ANDROID_LIBRARY };
            let lines: Vec<String> = dependencies.iter().map(|d| format!("    implementation({d})")).collect();
            fill(
                template,
                &[
                    ("plugins", plugins.join("\n")),
                    ("group", spec.group.clone()),
                    ("namespace", spec.package()),
                    ("dependencies", lines.join("\n")),
                ],
            )
        }
    };

    let package = spec.package();
    let source_dir = match (conventions.flat, spec.kind) {
        (true, _) => dir.join("src"),
        (false, ModuleKind::Multiplatform) => dir.join("src/commonMain/kotlin").join(package.replace('.', "/")),
        (false, _) => dir.join("src/main/kotlin").join(package.replace('.', "/")),
    };
    let name = spec.type_name();
    let mut files = vec![
        (dir.join("build.gradle.kts"), script),
        (source_dir.join(format!("{name}.kt")), format!("package {package}\n\ninternal object {name}\n")),
    ];
    if spec.kind == ModuleKind::AndroidApplication {
        let manifest = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <application />\n</manifest>\n";
        files.push((dir.join("src/main/AndroidManifest.xml"), manifest.to_owned()));
    }
    files.into_iter().map(|(path, text)| (crate::edit::portable(&path), text)).collect()
}

/// Adds [spec] to [project]: its files, its `include(...)`, and the root build script's declarations of its plugins.
pub fn add_module(project: &Project, spec: &ModuleSpec) -> Result<ChangeSet> {
    spec.validate(Some(project))?;
    let root = &project.root;
    let mut changes = ChangeSet::default();
    for (file, text) in module_files(spec, &Conventions::of(project)) {
        if root.join(&file).exists() {
            bail!("{} exists already", file.display());
        }
        changes.set(root, &file, text);
    }

    let Some(settings) = &project.settings_file else { bail!("the build has no settings script") };
    let settings = settings.strip_prefix(root).unwrap_or(settings).to_owned();
    let source = changes.current(root, &settings);
    changes.edit(root, &settings, &[include_edit(&source, &spec.path)]);

    let required = doctor::required_aliases(&spec.lvm_plugins(), spec.targets.iter().any(|t| t == "android"));
    let file = Path::new("build.gradle.kts");
    for line in doctor::missing_root_lines(project, &required) {
        let source = changes.current(root, file);
        changes.edit(root, file, &[crate::project::kts_edit::add_statement(&source, &["plugins"], &line)]);
    }
    Ok(changes)
}

/// `include(":path")` after the settings script's last include, or at its end.
fn include_edit(source: &str, path: &str) -> TextEdit {
    let script = Script::parse(source);
    let last = script
        .statements(script.all())
        .into_iter()
        .rfind(|s| s.chain == ["include"] && matches!(s.form, Form::Call { .. }));
    match last {
        Some(statement) => {
            let end = script.byte_span(&statement.tokens).end;
            TextEdit::insert(end, format!("\ninclude(\"{path}\")"))
        }
        None => {
            let separator = if source.is_empty() || source.ends_with('\n') { "" } else { "\n" };
            TextEdit::insert(source.len(), format!("{separator}\ninclude(\"{path}\")\n"))
        }
    }
}

/// A build to create.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProjectSpec {
    pub name: String,
    pub group: String,
    pub lvm_version: String,
    pub modules: Vec<ModuleSpec>,
    pub flat: bool,
    /// Resolves LVM from Maven local first, to try a version published there.
    pub maven_local: bool,
}

impl ProjectSpec {
    /// The usual start: a shared library for [targets] and, when [app] is set, an Android app that uses it.
    pub fn standard(
        name: &str,
        group: &str,
        lvm_version: &str,
        targets: Vec<String>,
        app: bool,
        plugins: Vec<String>,
    ) -> Self {
        let mut modules = vec![ModuleSpec {
            path: ":shared".into(),
            kind: ModuleKind::Multiplatform,
            framework: targets.iter().any(|t| t == "ios").then(|| "Shared".to_owned()),
            targets,
            plugins: plugins.clone(),
            group: group.into(),
            namespace: None,
            dependencies: Vec::new(),
        }];
        if app {
            let app_plugins = plugins.into_iter().filter(|p| p == "compose" || p == "quality").collect();
            modules.push(ModuleSpec {
                path: ":app".into(),
                kind: ModuleKind::AndroidApplication,
                targets: Vec::new(),
                plugins: app_plugins,
                group: group.into(),
                namespace: None,
                framework: None,
                dependencies: vec![":shared".into()],
            });
        }
        Self {
            name: name.into(),
            group: group.into(),
            lvm_version: lvm_version.into(),
            modules,
            flat: true,
            maven_local: false,
        }
    }
}

/// The files of a new build in [root], which must not hold a build yet.
pub fn new_project(root: &Path, spec: &ProjectSpec) -> Result<ChangeSet> {
    if crate::project::settings::settings_file(root).is_some() || root.join("build.gradle.kts").exists() {
        bail!("{} holds a Gradle build already", root.display());
    }
    if spec.name.is_empty() || spec.lvm_version.is_empty() {
        bail!("the build needs a name and an LVM version");
    }
    for module in &spec.modules {
        module.validate(None)?;
    }
    let conventions = Conventions { catalog: Some("lvmLibs".to_owned()), flat: spec.flat };
    let mut changes = ChangeSet::default();
    let mut write = |file: &str, text: String| changes.set(root, Path::new(file), text);

    let includes: Vec<String> = spec.modules.iter().map(|m| format!("include(\"{}\")", m.path)).collect();
    let (local_repository, local_resolution) = if spec.maven_local {
        ("        mavenLocal()\n", "\ndependencyResolutionManagement {\n    repositories { mavenLocal() }\n}\n")
    } else {
        ("", "")
    };
    write(
        "settings.gradle.kts",
        fill(
            SETTINGS,
            &[
                ("localRepository", local_repository.to_owned()),
                ("localResolution", local_resolution.to_owned()),
                ("lvmVersion", spec.lvm_version.clone()),
                ("flatLayout", spec.flat.to_string()),
                ("name", spec.name.clone()),
                ("includes", includes.join("\n")),
            ],
        ),
    );

    let required: BTreeSet<&str> = spec
        .modules
        .iter()
        .flat_map(|m| doctor::required_aliases(&m.lvm_plugins(), m.targets.iter().any(|t| t == "android")))
        .collect();
    // External plugins first, then LVM's, as the README lists them.
    let mut aliases: Vec<&str> = required.iter().copied().filter(|a| !a.starts_with("blueprint.")).collect();
    aliases.extend(required.iter().copied().filter(|a| a.starts_with("blueprint.")));
    let plugins: Vec<String> =
        aliases.iter().map(|alias| format!("    alias(lvmLibs.plugins.{alias}) apply false")).collect();
    write("build.gradle.kts", fill(ROOT, &[("plugins", plugins.join("\n"))]));
    let android = spec.modules.iter().any(|m| m.android());
    let properties =
        if android { format!("{GRADLE_PROPERTIES}android.useAndroidX=true\n") } else { GRADLE_PROPERTIES.to_owned() };
    write("gradle.properties", properties);
    write(".gitignore", GITIGNORE.to_owned());
    write("gradle/wrapper/gradle-wrapper.properties", WRAPPER_PROPERTIES.to_owned());
    write("gradlew", GRADLEW.to_owned());
    write("gradlew.bat", GRADLEW_BAT.to_owned());
    for module in &spec.modules {
        for (file, text) in module_files(module, &conventions) {
            changes.set(root, &file, text);
        }
    }
    changes.binaries.insert(PathBuf::from("gradle/wrapper/gradle-wrapper.jar"), WRAPPER_JAR.to_vec());
    changes.executables.push(PathBuf::from("gradlew"));
    Ok(changes)
}

/// Replaces each `{{name}}` of [template] with its value.
fn fill(template: &str, values: &[(&str, String)]) -> String {
    values.iter().fold(template.to_owned(), |text, (name, value)| text.replace(&format!("{{{{{name}}}}}"), value))
}

#[cfg(test)]
mod tests;
