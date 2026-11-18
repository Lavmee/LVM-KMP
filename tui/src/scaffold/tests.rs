use std::fs;
use std::path::Path;

use super::dependency::{self, DependencySpec};
use super::*;

fn consumer() -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    copy(&Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/consumer"), dir.path());
    dir
}

fn copy(from: &Path, to: &Path) {
    fs::create_dir_all(to).unwrap();
    for entry in fs::read_dir(from).unwrap() {
        let entry = entry.unwrap();
        let target = to.join(entry.file_name());
        if entry.path().is_dir() {
            copy(&entry.path(), &target);
        } else {
            fs::copy(entry.path(), target).unwrap();
        }
    }
}

fn after(changes: &ChangeSet, file: &str) -> String {
    changes.files[Path::new(file)].after.clone()
}

fn library(path: &str) -> ModuleSpec {
    ModuleSpec {
        path: path.into(),
        kind: ModuleKind::Multiplatform,
        targets: vec!["android".into(), "jvm".into()],
        plugins: vec!["quality".into(), "publish".into()],
        group: "com.example".into(),
        namespace: None,
        framework: None,
        dependencies: Vec::new(),
    }
}

#[test]
fn adds_a_module_in_the_build_s_layout_with_its_plugins_declared() {
    let dir = consumer();
    let project = Project::load(dir.path());

    let changes = add_module(&project, &library(":feature:chat-history")).unwrap();

    let script = after(&changes, "feature/chat-history/build.gradle.kts");
    assert!(script.contains("    alias(lvmLibs.plugins.blueprint.multiplatform)\n"), "{script}");
    assert!(script.contains("    alias(lvmLibs.plugins.blueprint.publish)\n"), "{script}");
    assert!(script.contains("group = \"com.example\""), "{script}");
    assert!(script.contains("        android()\n        jvm()\n"), "{script}");
    // The fixture uses the flat layout.
    let source = after(&changes, "feature/chat-history/src/ChatHistory.kt");
    assert_eq!(source, "package com.example.feature.chat_history\n\ninternal object ChatHistory\n");
    let settings = after(&changes, "settings.gradle.kts");
    assert!(settings.ends_with("include(\":core:data\")\ninclude(\":feature:chat-history\")\n"), "{settings}");
    // vanniktech's plugin is new to the build; the others are declared already.
    let root = after(&changes, "build.gradle.kts");
    assert!(root.contains("    alias(lvmLibs.plugins.vanniktech.mavenPublish) apply false\n"), "{root}");
    assert!(root.contains("    alias(lvmLibs.plugins.blueprint.publish) apply false\n"), "{root}");
    assert_eq!(root.matches("blueprint.multiplatform").count(), 1, "{root}");
}

#[test]
fn refuses_modules_that_would_not_build() {
    let dir = consumer();
    let project = Project::load(dir.path());
    assert!(add_module(&project, &library(":shared")).is_err(), "the build has :shared");
    assert!(add_module(&project, &library("feature")).is_err(), "a path starts with a colon");
    assert!(add_module(&project, &ModuleSpec { targets: Vec::new(), ..library(":x") }).is_err(), "no target");
    assert!(
        add_module(&project, &ModuleSpec { group: String::new(), ..library(":x") }).is_err(),
        "Android without a group"
    );
}

#[test]
fn a_standard_layout_puts_sources_under_their_package() {
    let spec = ModuleSpec {
        kind: ModuleKind::AndroidApplication,
        namespace: Some("com.example.app".into()),
        ..library(":app")
    };
    let files = module_files(&spec, &Conventions { catalog: None, flat: false });
    let paths: Vec<String> = files.iter().map(|(path, _)| path.display().to_string()).collect();
    assert_eq!(
        paths,
        ["app/build.gradle.kts", "app/src/main/kotlin/com/example/app/App.kt", "app/src/main/AndroidManifest.xml"]
    );
    assert!(files[0].1.contains("    id(\"tech.annexflow.lvm.android.application\")\n"), "{}", files[0].1);
    assert!(files[0].1.contains("android { namespace = \"com.example.app\" }"), "{}", files[0].1);
}

#[test]
fn creates_a_build_with_the_wrapper() {
    let dir = tempfile::tempdir().unwrap();
    let mut spec = ProjectSpec::standard(
        "Demo",
        "com.example",
        "0.4.0",
        vec!["android".into(), "jvm".into()],
        true,
        vec!["compose".into()],
    );
    spec.maven_local = true;

    let changes = new_project(dir.path(), &spec).unwrap();
    changes.apply(dir.path()).unwrap();

    let settings = fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap();
    assert!(settings.contains("        mavenLocal()\n        google()"), "{settings}");
    assert!(settings.contains("version \"0.4.0\""), "{settings}");
    assert!(settings.contains("include(\":shared\")\ninclude(\":app\")"), "{settings}");
    let root = fs::read_to_string(dir.path().join("build.gradle.kts")).unwrap();
    for alias in [
        "android.application",
        "android.kmp.library",
        "compose.multiplatform",
        "kotlin.compose",
        "kotlin.multiplatform",
        "blueprint.compose",
    ] {
        assert!(root.contains(&format!("alias(lvmLibs.plugins.{alias}) apply false")), "{alias} in {root}");
    }
    let app = fs::read_to_string(dir.path().join("app/build.gradle.kts")).unwrap();
    assert!(app.contains("implementation(project(\":shared\"))\n    implementation(lvmLibs.compose.runtime)"), "{app}");
    assert!(dir.path().join("gradle/wrapper/gradle-wrapper.jar").metadata().unwrap().len() > 1000);
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        assert_ne!(dir.path().join("gradlew").metadata().unwrap().permissions().mode() & 0o111, 0);
    }
    let project = Project::load(dir.path());
    assert_eq!(project.modules.iter().map(|m| m.path.as_str()).collect::<Vec<_>>(), [":app", ":shared"]);
    assert!(new_project(dir.path(), &spec).is_err(), "a build is there now");
}

#[test]
fn adds_a_library_to_a_source_set_with_what_it_needs() {
    let dir = consumer();
    let project = Project::load(dir.path());
    let shared = project.module(":shared").unwrap();
    assert_eq!(dependency::places(shared)[..2], ["commonMain".to_owned(), "commonTest".to_owned()]);

    let spec = DependencySpec {
        artifact: "lvm-storage".into(),
        source_set: Some("commonMain".into()),
        configuration: "implementation".into(),
    };
    let (changes, notes) = dependency::add(&project, shared, &spec).unwrap();

    let script = after(&changes, "shared/build.gradle.kts");
    assert!(
        script.contains("        implementation(lvmLibs.lvm.decompose)\n        implementation(lvmLibs.lvm.storage)\n"),
        "{script}"
    );
    assert!(script.contains("    alias(lvmLibs.plugins.kotlin.serialization)\n}"), "{script}");
    assert!(after(&changes, "build.gradle.kts").contains("alias(lvmLibs.plugins.kotlin.serialization) apply false"));
    assert_eq!(notes.len(), 1);

    let again = DependencySpec { artifact: "lvm-decompose".into(), ..spec };
    assert!(dependency::add(&project, shared, &again).is_err(), "it depends on lvm-decompose already");
}

#[test]
fn main_context_comes_as_its_plugin() {
    let dir = consumer();
    let project = Project::load(dir.path());
    let data = project.module(":core:data").unwrap();
    let spec = DependencySpec {
        artifact: "lvm-main-context".into(),
        source_set: Some("commonMain".into()),
        configuration: "implementation".into(),
    };

    let (changes, notes) = dependency::add(&project, data, &spec).unwrap();

    let script = after(&changes, "core/data/build.gradle.kts");
    assert!(script.contains("alias(lvmLibs.plugins.blueprint.main.context)"), "{script}");
    assert!(!script.contains("lvmLibs.lvm.main.context"), "{script}");
    assert!(after(&changes, "build.gradle.kts").contains("alias(lvmLibs.plugins.blueprint.main.context) apply false"));
    assert!(notes[0].contains("applies the plugin instead"));
}

#[test]
fn knows_the_libraries_of_its_release() {
    let libraries = dependency::libraries();
    assert!(libraries.contains(&"lvm-core"));
    assert!(libraries.contains(&"lvm-decompose-compose"));
}
