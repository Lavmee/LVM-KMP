//! Writes an `lvm.*` value to one of the places a build sets it: `gradle.properties`, the `lvm { }` block of the
//! settings script, or that of a module.

use std::path::{Path, PathBuf};

use anyhow::{Result, bail};
use serde::Serialize;
use serde_json::Value;

use super::keys::{self, KeyType};
use super::kts::{Form, Script};
use super::kts_edit;
use super::model::{Module, Project};
use crate::edit::{ChangeSet, TextEdit};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Layer {
    Properties,
    Settings,
    Module,
}

impl Layer {
    pub fn label(self) -> &'static str {
        match self {
            Self::Properties => "gradle.properties",
            Self::Settings => "settings lvm { }",
            Self::Module => "module lvm { }",
        }
    }
}

/// Keys that only a module sets, and the type of their value.
const MODULE_ONLY: &[&str] = &["android.namespace", "publish.name"];

/// The layers that can hold [key]: a module-only key lives in the module, a build-wide-only key never does.
pub fn layers(key: &str) -> Vec<Layer> {
    if MODULE_ONLY.contains(&key) {
        return vec![Layer::Module];
    }
    match keys::find(key) {
        Some(k) if k.build_wide_only => vec![Layer::Properties, Layer::Settings],
        Some(_) => vec![Layer::Properties, Layer::Settings, Layer::Module],
        None => Vec::new(),
    }
}

/// Parses what the user typed for [key]: an integer, a boolean, a comma-separated list or a string.
pub fn parse_input(key: &str, input: &str) -> Result<Value, String> {
    match keys::find(key) {
        Some(known) => known.parse(input),
        None => Ok(Value::String(input.to_owned())),
    }
}

/// A value as text to edit: lists comma-separated, as `gradle.properties` writes them.
pub fn input_text(value: Option<&Value>) -> String {
    match value {
        None | Some(Value::Null) => String::new(),
        Some(Value::String(text)) => text.clone(),
        Some(Value::Array(items)) => items.iter().map(|item| input_text(Some(item))).collect::<Vec<_>>().join(","),
        Some(other) => other.to_string(),
    }
}

/// The change that sets [key] to [value] in [layer], or removes it from there when [value] is `None`.
pub fn change(
    project: &Project,
    module: Option<&Module>,
    key: &str,
    layer: Layer,
    value: Option<&Value>,
) -> Result<ChangeSet> {
    if !layers(key).contains(&layer) {
        bail!("{key} can't be set in {}", layer.label());
    }
    let root = &project.root;
    let mut changes = ChangeSet::default();
    match layer {
        Layer::Properties => {
            let file = Path::new("gradle.properties");
            let source = changes.current(root, file);
            changes.edit(root, file, &properties_edit(&source, &format!("lvm.{key}"), value));
        }
        Layer::Settings => {
            let file = kotlin_script(project, project.settings_file.as_deref(), "settings script")?;
            let (blocks, name) = location(key, false);
            let source = changes.current(root, &file);
            changes.edit(root, &file, &script_edits(&source, key, &blocks, name, value));
        }
        Layer::Module => {
            let Some(module) = module else { bail!("no module to set {key} in") };
            let file =
                kotlin_script(project, module.build_file.as_deref(), &format!("build script of {}", module.path))?;
            let (blocks, name) = location(key, true);
            let source = changes.current(root, &file);
            let edits = match target_call(&source, &blocks, name, value) {
                Some(edit) => vec![edit],
                None => script_edits(&source, key, &blocks, name, value),
            };
            changes.edit(root, &file, &edits);
        }
    }
    Ok(changes)
}

fn kotlin_script(project: &Project, file: Option<&Path>, what: &str) -> Result<PathBuf> {
    let Some(file) = file else { bail!("the build has no {what}") };
    if file.extension().is_none_or(|ext| ext != "kts") {
        bail!("{} uses the Groovy DSL, which lvm doesn't edit", file.display());
    }
    Ok(file.strip_prefix(&project.root).unwrap_or(file).to_owned())
}

/// Where a key lives in `lvm { }`: the blocks and the property. A module sets the Android host tests and the iOS
/// architectures in its targets.
fn location(key: &str, module: bool) -> (Vec<&str>, &str) {
    match (module, key) {
        (true, "android.hostTests") => (vec!["lvm", "targets", "android"], "hostTests"),
        (true, "ios.architectures") => (vec!["lvm", "targets", "ios"], "architectures"),
        _ => {
            let parts: Vec<&str> = key.split('.').collect();
            let (name, sections) = parts.split_last().expect("a key has a name");
            (std::iter::once("lvm").chain(sections.iter().copied()).collect(), name)
        }
    }
}

fn script_edits(source: &str, key: &str, blocks: &[&str], name: &str, value: Option<&Value>) -> Vec<TextEdit> {
    match value {
        None => kts_edit::remove_value(source, blocks, name),
        Some(value) => {
            let code = kts_edit::kotlin_literal(value);
            if keys::find(key).is_some_and(|k| k.kind == KeyType::List) {
                kts_edit::set_list(source, blocks, name, &code)
            } else {
                kts_edit::set_value(source, blocks, name, &code)
            }
        }
    }
}

/// `android()` in `targets { }` becomes `android { hostTests = false }`: a second `android { }` would add the target
/// twice.
fn target_call(source: &str, blocks: &[&str], name: &str, value: Option<&Value>) -> Option<TextEdit> {
    let (target, parents) = blocks.split_last()?;
    if parents != ["lvm", "targets"] {
        return None;
    }
    let value = value?;
    let script = Script::parse(source);
    let mut body = script.all();
    for block in parents {
        body = script.statements(body).into_iter().find_map(|s| match s.form {
            Form::Block { body } if s.chain == [*block] => Some(body),
            _ => None,
        })?;
    }
    let call = script.statements(body).into_iter().find(|s| {
        s.chain == [*target] && matches!(&s.form, Form::Call { args, lambda: None, .. } if args.is_empty())
    })?;
    let span = script.byte_span(&call.tokens);
    Some(TextEdit::replace(span, format!("{target} {{ {name} = {} }}", kts_edit::kotlin_literal(value))))
}

fn properties_edit(source: &str, key: &str, value: Option<&Value>) -> Vec<TextEdit> {
    let line = source.lines().enumerate().filter(|(_, line)| {
        let trimmed = line.trim_start();
        trimmed.strip_prefix(key).is_some_and(|rest| rest.trim_start().starts_with(['=', ':']) || rest.is_empty())
    });
    let existing = line.last().map(|(index, _)| index);
    let text = value.map(|value| format!("{key}={}", input_text(Some(value)).replace('\\', "\\\\")));
    match (existing, text) {
        (Some(index), text) => {
            let start: usize = source.lines().take(index).map(|l| l.len() + 1).sum();
            let end = (start + source.lines().nth(index).map_or(0, str::len) + 1).min(source.len());
            vec![TextEdit::replace(start..end, text.map(|t| format!("{t}\n")).unwrap_or_default())]
        }
        (None, Some(text)) => {
            let separator = if source.is_empty() || source.ends_with('\n') { "" } else { "\n" };
            vec![TextEdit::insert(source.len(), format!("{separator}{text}\n"))]
        }
        (None, None) => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn write(root: &Path, file: &str, text: &str) {
        let path = root.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    fn after(changes: &ChangeSet, file: &str) -> String {
        changes.files[Path::new(file)].after.clone()
    }

    fn project() -> (tempfile::TempDir, Project) {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "plugins { id(\"tech.annexflow.lvm.settings\") }\n\ninclude(\":lib\")\n");
        write(root, "gradle.properties", "org.gradle.caching=true\nlvm.android.minSdk=24\n");
        write(
            root,
            "lib/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\n\nlvm {\n    targets {\n        android()\n        jvm()\n    }\n}\n",
        );
        let project = Project::load(root);
        (dir, project)
    }

    #[test]
    fn writes_gradle_properties() {
        let (_dir, project) = project();
        let set = change(&project, None, "android.minSdk", Layer::Properties, Some(&26.into())).unwrap();
        assert_eq!(after(&set, "gradle.properties"), "org.gradle.caching=true\nlvm.android.minSdk=26\n");
        let list =
            change(&project, None, "kotlin.optIns", Layer::Properties, Some(&serde_json::json!(["a", "b"]))).unwrap();
        assert_eq!(
            after(&list, "gradle.properties"),
            "org.gradle.caching=true\nlvm.android.minSdk=24\nlvm.kotlin.optIns=a,b\n"
        );
        let removed = change(&project, None, "android.minSdk", Layer::Properties, None).unwrap();
        assert_eq!(after(&removed, "gradle.properties"), "org.gradle.caching=true\n");
    }

    #[test]
    fn writes_the_settings_block() {
        let (_dir, project) = project();
        let set = change(&project, None, "publish.license.name", Layer::Settings, Some(&"MIT".into())).unwrap();
        assert_eq!(
            after(&set, "settings.gradle.kts"),
            "plugins { id(\"tech.annexflow.lvm.settings\") }\n\ninclude(\":lib\")\n\nlvm {\n    publish {\n        license {\n            name = \"MIT\"\n        }\n    }\n}\n"
        );
    }

    #[test]
    fn writes_a_target_value_into_the_target() {
        let (_dir, project) = project();
        let module = project.module(":lib").unwrap();
        let set = change(&project, Some(module), "android.hostTests", Layer::Module, Some(&false.into())).unwrap();
        assert!(after(&set, "lib/build.gradle.kts").contains("        android { hostTests = false }\n        jvm()\n"));
        let kotlin = change(&project, Some(module), "kotlin.explicitApi", Layer::Module, Some(&true.into())).unwrap();
        assert!(
            after(&kotlin, "lib/build.gradle.kts").contains("    kotlin {\n        explicitApi = true\n    }\n}\n")
        );
    }

    #[test]
    fn keeps_values_where_blueprint_reads_them() {
        assert_eq!(layers("publish.abi.enabled"), [Layer::Properties, Layer::Settings]);
        assert_eq!(layers("android.namespace"), [Layer::Module]);
        let (_dir, project) = project();
        let module = project.module(":lib").unwrap();
        assert!(change(&project, Some(module), "publish.abi.enabled", Layer::Module, Some(&false.into())).is_err());
        assert_eq!(
            parse_input("android.minSdk", "x").unwrap_err(),
            "lvm.android.minSdk must be an integer, but was 'x'"
        );
        assert_eq!(input_text(Some(&serde_json::json!(["a", "b"]))), "a,b");
    }
}
