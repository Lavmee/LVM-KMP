//! Moves a build to another LVM release: every place that names an LVM version, and what the new release changes for
//! the build's tools.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::Result;
use toml_edit::{DocumentMut, Item, Value as TomlValue};

use crate::edit::{ChangeSet, TextEdit};
use crate::maven::Catalog;
use crate::project::Project;
use crate::project::kts::{Form, Kind, Script};
use crate::project::model::LvmVersion;
use crate::version;

const GROUP: &str = "tech.annexflow.lvm";

/// The version the build uses now: the one its settings pin, or the one its reports name.
pub fn current_version(project: &Project) -> Option<String> {
    match &project.lvm {
        LvmVersion::Pinned(version) => Some(version.clone()),
        _ => project.reports.modules.values().next().map(|r| r.lvm_version.clone()),
    }
}

/// The change that names [to] wherever the build names an LVM version.
pub fn upgrade(project: &Project, to: &str) -> Result<ChangeSet> {
    let root = &project.root;
    let mut changes = ChangeSet::default();
    let settings = project.settings_file.as_deref().map(|f| f.strip_prefix(root).unwrap_or(f).to_owned());
    for file in files(root) {
        let source = fs::read_to_string(root.join(&file))?;
        let edited = if file.extension().is_some_and(|ext| ext == "toml") {
            upgrade_catalog(&source, to)
        } else {
            let mut edits = script_edits(&source, to);
            if Some(&file) == settings.as_ref() {
                edits.extend(catalog_version_edit(&source, to));
            }
            crate::edit::apply_edits(&source, &edits)
        };
        if edited != source {
            changes.set(root, &file, edited);
        }
    }
    Ok(changes)
}

/// Kotlin DSL scripts and version catalogs of the build, relative to its root, without build outputs.
fn files(root: &Path) -> Vec<PathBuf> {
    let mut files = Vec::new();
    collect(root, root, &mut files, 0);
    files.sort();
    files
}

fn collect(root: &Path, dir: &Path, files: &mut Vec<PathBuf>, depth: usize) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for entry in entries.filter_map(Result::ok) {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        if path.is_dir() {
            let skipped = name.starts_with('.') || matches!(name.as_str(), "build" | "target" | "node_modules" | "out");
            if !skipped && depth < 8 {
                collect(root, &path, files, depth + 1);
            }
        } else if name.ends_with(".gradle.kts") || name.ends_with(".versions.toml") {
            files.push(path.strip_prefix(root).unwrap_or(&path).to_owned());
        }
    }
}

/// `"tech.annexflow.lvm:lvm-core:0.3.2"` and `id("tech.annexflow.lvm.multiplatform") version "0.3.2"`, anywhere in a
/// script.
fn script_edits(source: &str, to: &str) -> Vec<TextEdit> {
    let script = Script::parse(source);
    let tokens = &script.tokens;
    let mut edits = Vec::new();
    for (i, token) in tokens.iter().enumerate() {
        let Kind::Str(Some(value)) = &token.kind else { continue };
        if let Some(coordinates) = with_version(value, to) {
            edits.push(TextEdit::replace(token.span.clone(), format!("\"{coordinates}\"")));
            continue;
        }
        // id("tech.annexflow.lvm.<plugin>") version "<version>"
        let is_plugin = value.starts_with(&format!("{GROUP}."))
            && i >= 2
            && script.text(i - 2) == "id"
            && tokens.get(i + 1).is_some_and(|t| t.kind == Kind::Close(')'))
            && tokens.get(i + 2).is_some_and(|t| t.kind == Kind::Ident && script.text(i + 2) == "version");
        if is_plugin && let Some(version) = tokens.get(i + 3).filter(|t| matches!(t.kind, Kind::Str(Some(_)))) {
            edits.push(TextEdit::replace(version.span.clone(), format!("\"{to}\"")));
        }
    }
    edits
}

/// `catalogVersion = "..."` in the settings `lvm { }` block, which pins the catalog apart from the plugin.
fn catalog_version_edit(source: &str, to: &str) -> Option<TextEdit> {
    let script = Script::parse(source);
    script.block_statements(script.all(), "lvm").into_iter().find_map(|statement| match statement.form {
        Form::Assign { value } if statement.chain == ["catalogVersion"] => {
            Some(TextEdit::replace(script.byte_span(&value), format!("\"{to}\"")))
        }
        _ => None,
    })
}

/// [coordinates] with their version replaced by [to], when they name an LVM artifact with a version.
fn with_version(coordinates: &str, to: &str) -> Option<String> {
    let parts: Vec<&str> = coordinates.split(':').collect();
    match parts.as_slice() {
        [GROUP, artifact, version, rest @ ..] if !version.is_empty() && *version != to => Some(
            std::iter::once(GROUP).chain([*artifact, to]).chain(rest.iter().copied()).collect::<Vec<_>>().join(":"),
        ),
        _ => None,
    }
}

/// A version catalog with every LVM library and plugin on [to]: their own versions, and the `[versions]` they refer to.
fn upgrade_catalog(source: &str, to: &str) -> String {
    let Ok(mut document) = source.parse::<DocumentMut>() else { return source.to_owned() };
    let mut references = BTreeSet::new();
    for table in ["libraries", "plugins"] {
        let Some(entries) = document.get_mut(table).and_then(Item::as_table_like_mut) else { continue };
        for (_, entry) in entries.iter_mut() {
            if let Some(text) = entry.as_str() {
                // "tech.annexflow.lvm:lvm-core:0.3.2", or "tech.annexflow.lvm.multiplatform:0.3.2" for a plugin
                let upgraded = with_version(text, to).or_else(|| plugin_with_version(text, to));
                if let Some(upgraded) = upgraded {
                    set_string(entry, &upgraded);
                }
                continue;
            }
            let Some(fields) = entry.as_table_like_mut() else { continue };
            let is_lvm = ["group", "module", "id"].iter().any(|key| {
                fields.get(key).and_then(Item::as_str).is_some_and(|v| {
                    v == GROUP || v.starts_with(&format!("{GROUP}:")) || v.starts_with(&format!("{GROUP}."))
                })
            });
            if !is_lvm {
                continue;
            }
            match fields.get_mut("version") {
                Some(item) if item.is_str() => set_string(item, to),
                Some(item) => {
                    if let Some(reference) = item.as_table_like().and_then(|v| v.get("ref")).and_then(Item::as_str) {
                        references.insert(reference.to_owned());
                    }
                }
                None => {}
            }
        }
    }
    if let Some(versions) = document.get_mut("versions").and_then(Item::as_table_like_mut) {
        for reference in references {
            if let Some(item) = versions.get_mut(&reference).filter(|item| item.is_str()) {
                set_string(item, to);
            }
        }
    }
    let edited = document.to_string();
    if edited == source { source.to_owned() } else { edited }
}

/// Replaces a string value, keeping the spaces and the comment around it.
fn set_string(item: &mut Item, text: &str) {
    let Some(value) = item.as_value_mut() else { return };
    let decor = value.decor().clone();
    *value = TomlValue::from(text);
    *value.decor_mut() = decor;
}

fn plugin_with_version(notation: &str, to: &str) -> Option<String> {
    let (id, version) = notation.split_once(':')?;
    (id.starts_with(&format!("{GROUP}.")) && version != to).then(|| format!("{id}:{to}"))
}

/// One tool in the comparison of two releases.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolRow {
    pub name: &'static str,
    /// What the build uses now, where lvm-tui knows it.
    pub build: Option<String>,
    pub current: Option<String>,
    pub target: Option<String>,
}

/// Versions that the catalogs name, as rows to compare.
const TOOLS: &[(&str, &str)] = &[
    ("kotlin", "Kotlin"),
    ("agp", "AGP"),
    ("composeMultiplatform", "Compose Multiplatform"),
    ("detekt", "detekt"),
    ("kotlinxCoroutines", "kotlinx.coroutines"),
    ("decompose", "Decompose"),
    ("ktor", "Ktor"),
];

/// The versions of the build's tools now, with [current] and with [target], and what to look out for.
pub fn compare(
    project: &Project,
    current: Option<&Catalog>,
    target: &Catalog,
    target_version: &str,
) -> (Vec<ToolRow>, Vec<String>) {
    let used = |key: &str| match key {
        "kotlin" | "agp" => project.toolchain.iter().find(|t| t.name == key).and_then(|t| t.actual.clone()),
        _ => None,
    };
    let rows: Vec<ToolRow> = TOOLS
        .iter()
        .filter(|(key, _)| target.contains_key(*key) || current.is_some_and(|c| c.contains_key(*key)))
        .map(|(key, name)| ToolRow {
            name,
            build: used(key),
            current: current.and_then(|c| c.get(*key).cloned()),
            target: target.get(*key).cloned(),
        })
        .collect();

    let mut notes = Vec::new();
    let from = current_version(project);
    match from.as_deref() {
        Some(from) if version::compare(target_version, from).is_eq() => {
            notes.push(format!("The build is on {from} already."))
        }
        Some(from) if version::compare(target_version, from).is_lt() => {
            notes.push(format!("{target_version} is older than {from}: this goes back to an older release."))
        }
        _ => {}
    }
    let catalog = &project.options.catalog_name;
    let follows_catalog = project
        .root_script
        .as_ref()
        .is_some_and(|s| s.plugins.iter().any(|p| p.notation.starts_with(&format!("alias:{catalog}.plugins.kotlin"))));
    if follows_catalog {
        notes.push(format!(
            "The root build script takes Kotlin, AGP and Compose from {catalog}, so they move with LVM."
        ));
    }
    let main_context = project.modules.iter().any(|m| m.has_plugin("main-context"));
    let kotlin = used("kotlin");
    if main_context
        && !follows_catalog
        && let (Some(kotlin), Some(tested)) = (kotlin.as_deref(), target.get("kotlin"))
        && bugfix_line(kotlin) != bugfix_line(tested)
    {
        notes.push(format!(
            "main-context in {target_version} needs Kotlin {tested} or a bugfix release of it; the build uses {kotlin}."
        ));
    }
    if target_version == crate::LVM_VERSION {
        let minimum = &crate::project::keys::keys().minimums.gradle;
        if let Some(gradle) = project.toolchain.iter().find(|t| t.name == "gradle").and_then(|t| t.actual.as_deref())
            && version::compare(gradle, minimum).is_lt()
        {
            notes.push(format!("{target_version} needs Gradle {minimum} or newer; the wrapper has {gradle}."));
        }
    }
    (rows, notes)
}

fn bugfix_line(version: &str) -> Option<(u64, u64, u64)> {
    let mut parts = version.split('-').next()?.split('.').map(|p| p.parse::<u64>().ok());
    Some((parts.next()??, parts.next()??, parts.next()?? / 10))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write(root: &Path, file: &str, text: &str) {
        let path = root.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    fn after(changes: &ChangeSet, file: &str) -> String {
        changes.files[Path::new(file)].after.clone()
    }

    #[test]
    fn names_the_new_version_everywhere_the_build_names_one() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(
            root,
            "settings.gradle.kts",
            "pluginManagement { includeBuild(\"build-logic\") }\nplugins {\n    id(\"tech.annexflow.lvm.settings\") version \"0.3.1\"\n}\nlvm {\n    catalogVersion = \"0.3.1\"\n}\ninclude(\":app\")\n",
        );
        write(
            root,
            "app/build.gradle.kts",
            "dependencies {\n    implementation(platform(\"tech.annexflow.lvm:lvm-bom:0.3.1\"))\n    implementation(\"tech.annexflow.lvm:lvm-core\")\n    implementation(\"com.other:lib:0.3.1\")\n}\n",
        );
        write(
            root,
            "build-logic/build.gradle.kts",
            "dependencies { compileOnly(\"tech.annexflow.lvm:lvm-blueprint-common:0.3.1\") }\n",
        );
        write(
            root,
            "gradle/libs.versions.toml",
            "[versions]\nlvm = \"0.3.1\"\nkotlin = \"2.4.21\"\n\n[libraries]\nlvm-core = { module = \"tech.annexflow.lvm:lvm-core\", version.ref = \"lvm\" }\nlvm-io = { group = \"tech.annexflow.lvm\", name = \"lvm-io\", version = \"0.3.1\" }\nlvm-bom = \"tech.annexflow.lvm:lvm-bom:0.3.1\" # aligned\nktor = { module = \"io.ktor:ktor-client-core\", version.ref = \"kotlin\" }\n\n[plugins]\nlvm-multiplatform = { id = \"tech.annexflow.lvm.multiplatform\", version.ref = \"lvm\" }\n",
        );
        write(root, "build/generated/x.gradle.kts", "\"tech.annexflow.lvm:lvm-core:0.3.1\"\n");
        let project = Project::load(root);

        let changes = upgrade(&project, "0.4.0").unwrap();

        assert_eq!(
            changes.files.keys().map(|p| p.display().to_string()).collect::<Vec<_>>(),
            [
                "app/build.gradle.kts",
                "build-logic/build.gradle.kts",
                "gradle/libs.versions.toml",
                "settings.gradle.kts"
            ]
        );
        let settings = after(&changes, "settings.gradle.kts");
        assert!(settings.contains("id(\"tech.annexflow.lvm.settings\") version \"0.4.0\""), "{settings}");
        assert!(settings.contains("catalogVersion = \"0.4.0\""), "{settings}");
        let app = after(&changes, "app/build.gradle.kts");
        assert!(app.contains("\"tech.annexflow.lvm:lvm-bom:0.4.0\""), "{app}");
        assert!(app.contains("\"tech.annexflow.lvm:lvm-core\""), "{app}");
        assert!(app.contains("\"com.other:lib:0.3.1\""), "{app}");
        assert!(after(&changes, "build-logic/build.gradle.kts").contains("lvm-blueprint-common:0.4.0"));
        let catalog = after(&changes, "gradle/libs.versions.toml");
        assert!(catalog.contains("lvm = \"0.4.0\""), "{catalog}");
        assert!(catalog.contains("kotlin = \"2.4.21\""), "{catalog}");
        assert!(catalog.contains("name = \"lvm-io\", version = \"0.4.0\""), "{catalog}");
        assert!(catalog.contains("lvm-bom = \"tech.annexflow.lvm:lvm-bom:0.4.0\" # aligned\n"), "{catalog}");
    }

    #[test]
    fn a_build_on_the_version_already_needs_no_change() {
        let dir = tempfile::tempdir().unwrap();
        write(dir.path(), "settings.gradle.kts", "plugins { id(\"tech.annexflow.lvm.settings\") version \"0.4.0\" }\n");
        let project = Project::load(dir.path());
        assert!(upgrade(&project, "0.4.0").unwrap().is_empty());
    }

    #[test]
    fn compares_tools_and_warns_about_main_context() {
        let dir = tempfile::tempdir().unwrap();
        write(
            dir.path(),
            "settings.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.settings\") version \"0.3.2\" }\ninclude(\":lib\")\n",
        );
        write(dir.path(), "gradle/libs.versions.toml", "[versions]\nkotlin = \"2.4.10\"\n");
        write(dir.path(), "lib/build.gradle.kts", "plugins { id(\"tech.annexflow.lvm.main-context\") }\n");
        let project = Project::load(dir.path());
        let current = Catalog::from([("kotlin".into(), "2.4.21".into()), ("agp".into(), "9.5.1".into())]);
        let target = Catalog::from([("kotlin".into(), "2.5.0".into()), ("agp".into(), "9.6.0".into())]);

        let (rows, notes) = compare(&project, Some(&current), &target, "0.4.0");

        assert_eq!(
            rows[0],
            ToolRow {
                name: "Kotlin",
                build: Some("2.4.10".into()),
                current: Some("2.4.21".into()),
                target: Some("2.5.0".into())
            }
        );
        assert!(notes.iter().any(|n| n.contains("main-context in 0.4.0 needs Kotlin 2.5.0")), "{notes:?}");
        let (_, notes) = compare(&project, Some(&current), &target, "0.3.1");
        assert!(notes.iter().any(|n| n.contains("older than 0.3.2")), "{notes:?}");
    }
}

/// `lvm-tui upgrade`: compares the build's release with [to], or with the latest, and names it everywhere.
pub fn run<S: crate::maven::Source>(
    project: &Project,
    releases: &crate::maven::Releases<S>,
    to: Option<&str>,
    dry_run: bool,
    out: &mut impl std::io::Write,
) -> Result<()> {
    // A release of today is missing from a list cached yesterday, so the command asks each time.
    let versions = releases.versions(true);
    let target = match (to, &versions) {
        (Some(to), _) => to.to_owned(),
        (None, Ok(versions)) => versions.last().cloned().unwrap_or_default(),
        (None, Err(error)) => anyhow::bail!("{error:#}: pass the version with --to"),
    };
    if let (Some(to), Ok(versions)) = (to, &versions)
        && !versions.iter().any(|v| v == to)
    {
        writeln!(out, "{to} is not on Maven Central, which has {}.", versions.join(", "))?;
    }
    let current = current_version(project);
    writeln!(out, "LVM {} -> {target}", current.as_deref().unwrap_or("?"))?;
    let current_catalog = current.as_deref().and_then(|v| releases.catalog(v).ok());
    if let Ok(target_catalog) = releases.catalog(&target) {
        let (rows, notes) = compare(project, current_catalog.as_ref(), &target_catalog, &target);
        for row in rows {
            let show = |v: &Option<String>| v.clone().unwrap_or_else(|| "-".to_owned());
            writeln!(
                out,
                "  {:<22} {:>10} -> {:<10} build: {}",
                row.name,
                show(&row.current),
                show(&row.target),
                show(&row.build)
            )?;
        }
        for note in notes {
            writeln!(out, "  note: {note}")?;
        }
    }
    let changes = upgrade(project, &target)?;
    if changes.is_empty() {
        writeln!(out, "Nothing to change: the build names no other LVM version.")?;
        return Ok(());
    }
    write!(out, "{}", changes.preview())?;
    if dry_run {
        writeln!(out, "Dry run: nothing written.")?;
    } else {
        changes.apply(&project.root)?;
        writeln!(out, "Upgraded to {target}. Run ./gradlew lvmReport build to check the build.")?;
    }
    Ok(())
}

#[cfg(test)]
mod run_tests {
    use super::*;
    use crate::maven::Releases;
    use crate::maven::fake::{Fake, metadata};

    fn build_on_0_3_2() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        fs::write(
            dir.path().join("settings.gradle.kts"),
            "plugins { id(\"tech.annexflow.lvm.settings\") version \"0.3.2\" }\n",
        )
        .unwrap();
        dir
    }

    #[test]
    fn upgrades_to_the_given_version_without_maven_central() {
        let dir = build_on_0_3_2();
        let project = Project::load(dir.path());
        let offline = Fake::offline();
        let releases = Releases::new(&offline, None, None);

        let mut out = Vec::new();
        run(&project, &releases, Some("0.4.0"), true, &mut out).unwrap();
        let text = String::from_utf8(out).unwrap();
        assert!(text.contains("LVM 0.3.2 -> 0.4.0"), "{text}");
        assert!(text.contains("+plugins { id(\"tech.annexflow.lvm.settings\") version \"0.4.0\" }"), "{text}");
        assert!(text.ends_with("Dry run: nothing written.\n"), "{text}");
        assert!(fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap().contains("0.3.2"));

        run(&project, &releases, Some("0.4.0"), false, &mut Vec::new()).unwrap();
        assert!(fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap().contains("\"0.4.0\""));
        assert!(
            run(&project, &releases, None, true, &mut Vec::new()).is_err(),
            "without Maven Central, --to is needed"
        );
    }

    #[test]
    fn upgrades_to_a_release_newer_than_the_cached_list() {
        let cache = tempfile::tempdir().unwrap();
        let yesterday = metadata(&["0.3.2"]);
        let before = Fake::new(&[("maven-metadata.xml", &yesterday)]);
        Releases::new(&before, Some(cache.path().to_owned()), None).versions(false).unwrap();
        let today = metadata(&["0.3.2", "0.4.0"]);
        let after = Fake::new(&[("maven-metadata.xml", &today)]);
        let offline = Fake::offline();

        for (network, source) in [("online", &after), ("offline", &offline)] {
            let dir = build_on_0_3_2();
            let releases = Releases::new(source, Some(cache.path().to_owned()), None);
            run(&Project::load(dir.path()), &releases, None, false, &mut Vec::new()).unwrap();
            let settings = fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap();
            assert!(settings.contains("\"0.4.0\""), "{network}: {settings}");
        }
    }
}
