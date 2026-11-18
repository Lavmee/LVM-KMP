//! What `settings.gradle.kts` says about a build: its modules, the LVM version and the settings `lvm { }` block.

use std::path::{Path, PathBuf};

use serde::Serialize;

use super::kts::{Form, Kind, Literal, Script};
use super::lvm_block::{ScriptValue, read_values};

pub const SETTINGS_PLUGIN: &str = "tech.annexflow.lvm.settings";

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsScript {
    pub file: PathBuf,
    pub root_name: Option<String>,
    pub includes: Vec<Include>,
    /// `id("tech.annexflow.lvm.settings")` in `plugins { }`, with its version when it has one.
    pub lvm_plugin: Option<PluginPin>,
    /// Builds that `pluginManagement { includeBuild(...) }` includes: a build that includes LVM's blueprint gets the
    /// plugins from source.
    pub plugin_builds: Vec<String>,
    pub options: SettingsOptions,
    /// Build-wide values that the settings `lvm { }` block sets.
    pub values: Vec<ScriptValue>,
    /// The script uses the Groovy DSL, which this reader understands only partly.
    pub groovy: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Include {
    pub path: String,
    pub line: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct PluginPin {
    pub version: Option<String>,
    pub line: usize,
}

/// The settings plugin's own options, where the script sets them.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsOptions {
    pub catalog: Option<bool>,
    pub catalog_name: Option<String>,
    pub catalog_version: Option<String>,
    pub repositories: Option<bool>,
}

const OPTIONS: &[&str] = &["catalog", "catalogName", "catalogVersion", "repositories", "values"];

/// Finds `settings.gradle.kts` or `settings.gradle` in [root].
pub fn settings_file(root: &Path) -> Option<PathBuf> {
    ["settings.gradle.kts", "settings.gradle"].iter().map(|name| root.join(name)).find(|path| path.is_file())
}

impl SettingsScript {
    pub fn read(file: &Path) -> Option<Self> {
        let source = std::fs::read_to_string(file).ok()?;
        let mut settings = Self::parse(&source);
        settings.file = file.to_owned();
        settings.groovy = file.extension().is_some_and(|ext| ext == "gradle");
        Some(settings)
    }

    pub fn parse(source: &str) -> Self {
        let script = Script::parse(source);
        let mut settings = Self::default();
        for statement in script.statements(script.all()) {
            match (statement.chain.iter().map(String::as_str).collect::<Vec<_>>().as_slice(), &statement.form) {
                (["rootProject", "name"], Form::Assign { value }) => {
                    if let Some(Literal::Str(name)) = script.literal(value.clone()) {
                        settings.root_name = Some(name);
                    }
                }
                (["include"], Form::Call { args, .. }) => {
                    let line = script.line(statement.tokens.start);
                    for path in script.string_args(args).unwrap_or_default() {
                        settings.includes.push(Include { path: normalize_path(&path), line });
                    }
                }
                (["plugins"], Form::Block { body }) => settings.lvm_plugin = settings_plugin(&script, body.clone()),
                (["pluginManagement"], Form::Block { body }) => {
                    for (_, args) in script.calls(body.clone(), "includeBuild") {
                        settings.plugin_builds.extend(script.string_args(&args).unwrap_or_default());
                    }
                }
                (["lvm"], Form::Block { body }) => {
                    settings.options = options(&script, body.clone());
                    settings.values = read_values(&script, body.clone(), OPTIONS);
                }
                _ => {}
            }
        }
        settings
    }
}

/// `include("app")` and `include(":app")` name the same project.
fn normalize_path(path: &str) -> String {
    if path.starts_with(':') { path.to_owned() } else { format!(":{path}") }
}

fn settings_plugin(script: &Script, body: std::ops::Range<usize>) -> Option<PluginPin> {
    script.statements(body).into_iter().find_map(|statement| {
        let Form::Call { args, rest, .. } = &statement.form else {
            return None;
        };
        if statement.chain != ["id"] || script.string_args(args)? != [SETTINGS_PLUGIN] {
            return None;
        }
        // id("...") version "x.y.z"
        let version = rest.clone().zip(rest.clone().skip(1)).find_map(|(name, value)| {
            match (&script.tokens[name].kind, &script.tokens[value].kind) {
                (Kind::Ident, Kind::Str(Some(version))) if script.text(name) == "version" => Some(version.clone()),
                _ => None,
            }
        });
        Some(PluginPin { version, line: script.line(statement.tokens.start) })
    })
}

fn options(script: &Script, body: std::ops::Range<usize>) -> SettingsOptions {
    let mut options = SettingsOptions::default();
    for statement in script.statements(body) {
        let Form::Assign { value } = &statement.form else {
            continue;
        };
        let literal = script.literal(value.clone());
        match (statement.chain.join(".").as_str(), literal) {
            ("catalog", Some(Literal::Bool(value))) => options.catalog = Some(value),
            ("catalogName", Some(Literal::Str(value))) => options.catalog_name = Some(value),
            ("catalogVersion", Some(Literal::Str(value))) => options.catalog_version = Some(value),
            ("repositories", Some(Literal::Bool(value))) => options.repositories = Some(value),
            _ => {}
        }
    }
    options
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::project::lvm_block::ValueOp;

    const CONSUMER: &str = r#"
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

plugins {
    id("tech.annexflow.lvm.settings") version "0.3.2"
}

lvm {
    catalogName = "lvmCatalog"
    repositories = false
    android { minSdk = 26 }
}

rootProject.name = "Consumer"
include(":app", "shared")
include(":feature:chat")
"#;

    #[test]
    fn reads_modules_version_options_and_values() {
        let settings = SettingsScript::parse(CONSUMER);
        assert_eq!(settings.root_name.as_deref(), Some("Consumer"));
        let paths: Vec<_> = settings.includes.iter().map(|i| (i.path.as_str(), i.line)).collect();
        assert_eq!(paths, [(":app", 17), (":shared", 17), (":feature:chat", 18)]);
        assert_eq!(settings.lvm_plugin, Some(PluginPin { version: Some("0.3.2".into()), line: 7 }));
        assert_eq!(settings.options.catalog_name.as_deref(), Some("lvmCatalog"));
        assert_eq!(settings.options.repositories, Some(false));
        assert_eq!(settings.options.catalog, None);
        assert_eq!(settings.values.len(), 1);
        assert_eq!(settings.values[0].key, "android.minSdk");
        assert_eq!(settings.values[0].op, ValueOp::Set(26.into()));
    }

    #[test]
    fn a_build_with_blueprint_from_source_has_no_version() {
        let settings = SettingsScript::parse(
            "pluginManagement { includeBuild(\"blueprint\") }\nplugins { id(\"tech.annexflow.lvm.settings\") }\n",
        );
        assert_eq!(settings.lvm_plugin.and_then(|p| p.version), None);
        assert_eq!(settings.plugin_builds, ["blueprint"]);
    }
}
