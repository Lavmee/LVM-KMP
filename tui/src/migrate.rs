//! Moves a module's sources from `src/<sourceSet>/kotlin` to LVM's flat layout: `src/`, `test/`, `src@<name>/` and
//! `test@<name>/`, with the module's root package left out of the directories.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Result, bail};
use serde_json::Value;

use crate::edit::{ChangeSet, Move};
use crate::project::kts_edit;
use crate::project::model::{Module, Project};

/// The flat directory of a Kotlin Multiplatform source set, as blueprint names it, or `None` for one it doesn't move.
pub fn kmp_directory(source_set: &str) -> Option<String> {
    match source_set {
        "commonMain" => Some("src".to_owned()),
        "commonTest" => Some("test".to_owned()),
        name => {
            if let Some(platform) = name.strip_suffix("Main") {
                Some(format!("src@{platform}"))
            } else {
                name.strip_suffix("Test").map(|platform| format!("test@{platform}"))
            }
        }
    }
}

/// The flat directory of an Android source set: `main`, `test`, `androidTest`, `testFixtures`, or one of these for a
/// build type, a flavor or a variant, such as `testDebug` -> `test@debug`.
pub fn android_directory(source_set: &str) -> String {
    for kind in ["androidTest", "testFixtures", "test"] {
        if source_set == kind {
            return kind.to_owned();
        }
        if let Some(variant) = source_set.strip_prefix(kind).filter(|rest| rest.starts_with(char::is_uppercase)) {
            return format!("{kind}@{}", lower_first(variant));
        }
    }
    if source_set == "main" { "src".to_owned() } else { format!("src@{source_set}") }
}

/// Whether blueprint compiles the Java files in the flat directory of [source_set]: those of every source set of a module
/// without Kotlin Multiplatform, and in a Kotlin Multiplatform module, those of the JVM target and, after `withJava()`,
/// of the Android target.
fn holds_java(kmp: bool, source_set: &str) -> bool {
    !kmp || ["jvmMain", "jvmTest", "androidMain", "androidHostTest", "androidDeviceTest"].contains(&source_set)
}

fn lower_first(text: &str) -> String {
    let mut chars = text.chars();
    chars.next().map(|first| first.to_lowercase().chain(chars).collect()).unwrap_or_default()
}

/// Adds to [changes] the moves of [module]'s sources to the flat layout, and turns the layout on for the module
/// unless it already is.
pub fn flat_layout(project: &Project, module: &Module, changes: &mut ChangeSet) -> Result<()> {
    let root = &project.root;
    let module_dir = PathBuf::from(&module.dir);
    let src = root.join(&module_dir).join("src");
    let kmp = module.has_plugin("multiplatform");

    // (source set, its language directory, a file in it)
    let mut sources: Vec<(String, PathBuf, PathBuf)> = Vec::new();
    for entry in fs::read_dir(&src).into_iter().flatten().filter_map(Result::ok) {
        let source_set = entry.file_name().to_string_lossy().into_owned();
        for language in ["kotlin", "java"] {
            let dir = entry.path().join(language);
            if !dir.is_dir() {
                continue;
            }
            let mut files = Vec::new();
            collect_files(&dir, &mut files);
            if !holds_java(kmp, &source_set)
                && files.iter().any(|file| file.extension().is_some_and(|ext| ext == "java"))
            {
                bail!(
                    "{} has Java sources in src/{source_set}/{language}, which the flat layout of a Kotlin \
                     Multiplatform module holds only in the source sets of its JVM and Android targets",
                    module.path
                );
            }
            sources.extend(files.into_iter().map(|file| (source_set.clone(), dir.clone(), file)));
        }
    }
    if sources.is_empty() {
        bail!("{} has no sources in src/<sourceSet>/kotlin", module.path);
    }
    sources.sort();

    let root_package = root_package(sources.iter().map(|(_, _, file)| file.as_path()));
    let mut destinations = std::collections::BTreeSet::new();
    for (source_set, language_dir, file) in &sources {
        let directory = if kmp {
            kmp_directory(source_set)
                .ok_or_else(|| anyhow::anyhow!("{}: source set {source_set} has no flat directory", module.path))?
        } else {
            android_directory(source_set)
        };
        let inside = file.strip_prefix(language_dir).unwrap_or(file);
        let package_path: PathBuf = root_package.iter().collect();
        let inside = inside.strip_prefix(&package_path).unwrap_or(inside);
        let to = module_dir.join(&directory).join(inside);
        let from = file.strip_prefix(root).unwrap_or(file).to_owned();
        if root.join(&to).exists() || !destinations.insert(to.clone()) {
            bail!("{} would overwrite {}", from.display(), to.display());
        }
        changes.moves.push(Move { from: crate::edit::portable(&from), to: crate::edit::portable(&to) });
    }

    let flat = module.values.iter().find(|row| row.key == "kotlin.flatLayout").and_then(|row| row.value.as_ref());
    if flat != Some(&Value::Bool(true)) {
        let Some(build_file) = &module.build_file else { bail!("{} has no build script", module.path) };
        let file = build_file.strip_prefix(root).unwrap_or(build_file);
        let source = changes.current(root, file);
        changes.edit(root, file, &kts_edit::set_value(&source, &["lvm", "kotlin"], "flatLayout", "true"));
    }
    Ok(())
}

fn collect_files(dir: &Path, files: &mut Vec<PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    let mut entries: Vec<_> = entries.filter_map(Result::ok).map(|e| e.path()).collect();
    entries.sort();
    for path in entries {
        if path.is_dir() {
            collect_files(&path, files);
        } else {
            files.push(path);
        }
    }
}

/// The longest package that every Kotlin file of the module is in, as its segments.
fn root_package<'a>(files: impl Iterator<Item = &'a Path>) -> Vec<String> {
    let mut common: Option<Vec<String>> = None;
    for file in files.filter(|f| f.extension().is_some_and(|ext| ext == "kt")) {
        let package = fs::read_to_string(file)
            .ok()
            .and_then(|text| {
                text.lines().find_map(|line| {
                    line.trim().strip_prefix("package ").map(|p| p.trim().trim_end_matches(';').to_owned())
                })
            })
            .unwrap_or_default();
        let segments: Vec<String> = package.split('.').filter(|s| !s.is_empty()).map(str::to_owned).collect();
        common = Some(match common {
            None => segments,
            Some(previous) => {
                previous.iter().zip(&segments).take_while(|(a, b)| a == b).map(|(a, _)| a.clone()).collect()
            }
        });
    }
    common.unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::project::Project;

    fn write(root: &Path, file: &str, text: &str) {
        let path = root.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    #[test]
    fn names_flat_directories_as_blueprint_does() {
        assert_eq!(kmp_directory("commonMain").as_deref(), Some("src"));
        assert_eq!(kmp_directory("iosTest").as_deref(), Some("test@ios"));
        assert_eq!(kmp_directory("androidHostTest").as_deref(), Some("test@androidHost"));
        assert_eq!(android_directory("main"), "src");
        assert_eq!(android_directory("testDebug"), "test@debug");
        assert_eq!(android_directory("androidTestFreeDebug"), "androidTest@freeDebug");
        assert_eq!(android_directory("paid"), "src@paid");
        assert_eq!(android_directory("testFixtures"), "testFixtures");
    }

    #[test]
    fn moves_java_sources_of_the_jvm_target_next_to_its_kotlin_sources() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "include(\":lib\")\n");
        write(
            root,
            "lib/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { jvm() } }\n",
        );
        write(root, "lib/src/jvmMain/java/com/example/lib/NativeMarkdown.java", "package com.example.lib;\n");
        write(root, "lib/src/jvmMain/kotlin/com/example/lib/Markdown.kt", "package com.example.lib\n");
        let project = Project::load(root);

        let mut changes = ChangeSet::default();
        flat_layout(&project, project.module(":lib").unwrap(), &mut changes).unwrap();

        let destinations: Vec<_> = changes.moves.iter().map(|m| m.to.to_string_lossy().into_owned()).collect();
        assert_eq!(destinations, ["lib/src@jvm/NativeMarkdown.java", "lib/src@jvm/Markdown.kt"]);
    }

    #[test]
    fn refuses_java_sources_of_common_code() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "include(\":lib\")\n");
        write(
            root,
            "lib/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { jvm() } }\n",
        );
        write(root, "lib/src/commonMain/java/com/example/Main.java", "package com.example;\n");
        let project = Project::load(root);

        let error = flat_layout(&project, project.module(":lib").unwrap(), &mut ChangeSet::default()).unwrap_err();
        assert!(error.to_string().contains("Java"), "{error}");
    }

    #[test]
    fn moves_sources_without_the_root_package_and_turns_the_layout_on() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "include(\":lib\")\n");
        write(
            root,
            "lib/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { jvm() } }\n",
        );
        write(root, "lib/src/commonMain/kotlin/com/example/lib/Api.kt", "package com.example.lib\n");
        write(root, "lib/src/commonMain/kotlin/com/example/lib/internal/Impl.kt", "package com.example.lib.internal\n");
        write(root, "lib/src/jvmTest/kotlin/com/example/lib/ApiTest.kt", "package com.example.lib\n");
        write(root, "lib/src/commonMain/resources/data.txt", "kept");
        let project = Project::load(root);

        let mut changes = ChangeSet::default();
        flat_layout(&project, project.module(":lib").unwrap(), &mut changes).unwrap();

        let moves: Vec<_> = changes
            .moves
            .iter()
            .map(|m| (m.from.to_string_lossy().into_owned(), m.to.to_string_lossy().into_owned()))
            .collect();
        assert_eq!(
            moves,
            [
                ("lib/src/commonMain/kotlin/com/example/lib/Api.kt".into(), "lib/src/Api.kt".into()),
                (
                    "lib/src/commonMain/kotlin/com/example/lib/internal/Impl.kt".into(),
                    "lib/src/internal/Impl.kt".into()
                ),
                ("lib/src/jvmTest/kotlin/com/example/lib/ApiTest.kt".into(), "lib/test@jvm/ApiTest.kt".into()),
            ]
        );
        let script = &changes.files[Path::new("lib/build.gradle.kts")].after;
        assert!(script.contains("kotlin { flatLayout = true }"), "{script}");

        changes.apply(root).unwrap();
        assert!(root.join("lib/src/internal/Impl.kt").is_file());
        assert!(root.join("lib/src/commonMain/resources/data.txt").is_file());
        assert!(!root.join("lib/src/commonMain/kotlin").exists());
    }

    #[test]
    fn moves_java_sources_next_to_the_kotlin_sources_of_an_android_module() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "include(\":app\")\n");
        write(root, "app/build.gradle.kts", "plugins { id(\"tech.annexflow.lvm.android.application\") }\n");
        write(root, "app/src/main/java/com/example/NativeMarkdown.java", "package com.example;\n");
        write(root, "app/src/main/kotlin/com/example/Markdown.kt", "package com.example\n");
        let project = Project::load(root);

        let mut changes = ChangeSet::default();
        flat_layout(&project, project.module(":app").unwrap(), &mut changes).unwrap();

        let destinations: Vec<_> = changes.moves.iter().map(|m| m.to.to_string_lossy().into_owned()).collect();
        assert_eq!(destinations, ["app/src/NativeMarkdown.java", "app/src/Markdown.kt"]);
    }
}
