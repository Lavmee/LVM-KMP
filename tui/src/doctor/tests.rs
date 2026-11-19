use std::fs;
use std::path::Path;

use super::*;

/// A copy of the consumer fixture, which has one problem: `:core:data` keeps the standard layout in a flat build.
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

fn replace(root: &Path, file: &str, from: &str, to: &str) {
    let path = root.join(file);
    let text = fs::read_to_string(&path).unwrap();
    assert!(text.contains(from), "{file} has no {from:?}");
    fs::write(path, text.replacen(from, to, 1)).unwrap();
}

fn healthy(root: &Path) -> Environment {
    Environment {
        os: "macos".into(),
        java: Some(21),
        android_sdk: Some(root.to_owned()),
        xcode: Some(true),
        local_properties_tracked: false,
    }
}

fn rules(findings: &[Finding]) -> Vec<(&'static str, Severity)> {
    findings.iter().map(|f| (f.rule, f.severity)).collect()
}

fn fix_all(project: &Project, findings: &[Finding]) -> ChangeSet {
    let mut changes = ChangeSet::default();
    for fix in findings.iter().filter_map(|f| f.fix.as_ref()) {
        fix.apply(project, &mut changes).unwrap();
    }
    changes
}

#[test]
fn finds_sources_that_the_flat_layout_would_reject_and_moves_them() {
    let dir = consumer();
    let project = Project::load(dir.path());

    let findings = diagnose(&project, &healthy(dir.path()));

    assert_eq!(rules(&findings), [("flat-layout", Severity::Error)]);
    assert_eq!(findings[0].module.as_deref(), Some(":core:data"));
    fix_all(&project, &findings).apply(dir.path()).unwrap();
    assert!(dir.path().join("core/data/src/Repository.kt").is_file());
    assert!(diagnose(&Project::load(dir.path()), &healthy(dir.path())).is_empty());
}

#[test]
fn finds_and_fixes_mistakes_in_the_build_scripts() {
    let dir = consumer();
    let root = dir.path();
    fs::remove_dir_all(root.join("core")).unwrap();
    replace(root, "settings.gradle.kts", "include(\":core:data\")\n", "");
    replace(root, "settings.gradle.kts", "lvm {\n", "lvm {\n    catalogName = \"lvm\"\n");
    replace(
        root,
        "gradle.properties",
        "lvm.kotlin.warningsAsErrors=true",
        "lvm.android.minSDK=26\nlvm.kotlin.warningsAsErrors=yes",
    );
    replace(root, "build.gradle.kts", "    alias(lvmLibs.plugins.detekt) apply false\n", "");
    replace(root, "build.gradle.kts", "    alias(lvmLibs.plugins.blueprint.quality) apply false\n", "");
    replace(root, "app/build.gradle.kts", "    android { namespace = \"com.example.app\" }\n", "");
    let project = Project::load(root);

    let findings = diagnose(&project, &healthy(root));

    assert_eq!(
        rules(&findings),
        [
            ("namespace", Severity::Error),
            ("root-plugins", Severity::Error),
            ("invalid-value", Severity::Error),
            ("catalog-name", Severity::Error),
            ("root-plugins", Severity::Warning),
            ("unknown-key", Severity::Warning),
        ]
    );
    let unknown = findings.iter().find(|f| f.rule == "unknown-key").unwrap();
    assert!(unknown.message.contains("did you mean lvm.android.minSdk?"), "{}", unknown.message);
    assert_eq!(unknown.line, Some(3));

    // The catalog named lvm can't be fixed for the user, and neither can the invalid value; the rest can.
    let changes = fix_all(&project, &findings);
    let root_script = &changes.files[Path::new("build.gradle.kts")].after;
    assert!(root_script.contains("    alias(lvm.plugins.blueprint.quality) apply false\n"), "{root_script}");
    assert!(root_script.contains("    alias(lvm.plugins.detekt) apply false\n}"), "{root_script}");
    assert!(changes.files[Path::new("gradle.properties")].after.contains("lvm.android.minSdk=26\n"));
    assert!(changes.files[Path::new("app/build.gradle.kts")].after.contains("group = \"com.example\""));
    changes.apply(root).unwrap();
    let remaining = diagnose(&Project::load(root), &healthy(root));
    assert_eq!(rules(&remaining), [("invalid-value", Severity::Error), ("catalog-name", Severity::Error)]);
}

#[test]
fn knows_the_values_that_only_a_module_sets() {
    let dir = consumer();
    let root = dir.path();
    replace(
        root,
        "shared/build.gradle.kts",
        "lvm {\n",
        "lvm {\n    kotlin { excludedOptIns.add(\"kotlin.time.ExperimentalTime\") }\n",
    );

    let findings = diagnose(&Project::load(root), &healthy(root));

    assert!(findings.iter().all(|f| f.rule != "unknown-key"), "{:?}", rules(&findings));
}

#[test]
fn takes_root_plugins_that_the_build_names_in_its_own_catalog_and_leaves_them_alone() {
    let dir = consumer();
    let root = dir.path();
    fs::write(
        root.join("build.gradle.kts"),
        "plugins {\n    alias(libs.plugins.android.app) apply false\n    alias(libs.plugins.android.kmp) apply false\n    alias(libs.plugins.kmp) apply false\n    alias(libs.plugins.compose) apply false\n    alias(libs.plugins.jetbrains.compose) apply false\n    alias(libs.plugins.detekt) apply false\n    alias(libs.plugins.lvm.multiplatform) apply false\n    alias(libs.plugins.lvm.compose) apply false\n    alias(libs.plugins.lvm.android.application) apply false\n    alias(libs.plugins.lvm.quality) apply false\n}\n",
    )
    .unwrap();
    fs::write(
        root.join("gradle/libs.versions.toml"),
        r#"[versions]
lvm = "0.5.0"

[plugins]
android-app = { id = "com.android.application", version = "9.5.1" }
android_kmp = { id = "com.android.kotlin.multiplatform.library", version = "9.5.1" }
kmp = "org.jetbrains.kotlin.multiplatform:2.4.21"
compose = { id = "org.jetbrains.kotlin.plugin.compose", version = "2.4.21" }
jetbrains-compose = { id = "org.jetbrains.compose", version = "1.12.2" }
detekt = { id = "io.gitlab.arturbosch.detekt", version = "1.23.8" }
lvm-multiplatform = { id = "tech.annexflow.lvm.multiplatform", version.ref = "lvm" }
lvm-compose = { id = "tech.annexflow.lvm.compose", version.ref = "lvm" }
lvm-android-application = { id = "tech.annexflow.lvm.android.application", version.ref = "lvm" }
"lvm.quality" = { id = "tech.annexflow.lvm.quality", version.ref = "lvm" }
"#,
    )
    .unwrap();
    let project = Project::load(root);

    let findings = diagnose(&project, &healthy(root));

    assert_eq!(rules(&findings), [("flat-layout", Severity::Error)]);
    let changes = fix_all(&project, &findings);
    assert!(!changes.files.contains_key(Path::new("build.gradle.kts")));
}

#[test]
fn takes_a_catalog_entry_for_its_plugin_id_not_its_name() {
    let dir = consumer();
    let root = dir.path();
    replace(root, "build.gradle.kts", "alias(lvmLibs.plugins.detekt)", "alias(libs.plugins.detekt)");
    fs::write(
        root.join("gradle/libs.versions.toml"),
        "[plugins]\ndetekt = { id = \"dev.detekt\", version = \"2.0.0\" }\n",
    )
    .unwrap();
    let project = Project::load(root);

    let findings = diagnose(&project, &healthy(root));

    let missing = findings.iter().find(|f| f.rule == "root-plugins").expect("a root-plugins finding");
    assert_eq!(missing.severity, Severity::Warning);
    assert!(missing.message.contains("io.gitlab.arturbosch.detekt"), "{}", missing.message);
    assert_eq!(
        missing.fix,
        Some(Fix::DeclareRootPlugins { lines: vec!["alias(lvmLibs.plugins.detekt) apply false".into()] })
    );
}

#[test]
fn checks_a_module_that_applies_lvm_s_plugins_through_the_build_s_own_catalog() {
    let dir = consumer();
    let root = dir.path();
    fs::write(root.join("core/data/build.gradle.kts"), "plugins {\n    alias(libs.plugins.lvm.jvm)\n}\n").unwrap();
    fs::write(
        root.join("gradle/libs.versions.toml"),
        "[plugins]\nlvm-jvm = { id = \"tech.annexflow.lvm.jvm\", version = \"0.5.0\" }\nkotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version = \"2.4.21\" }\n",
    )
    .unwrap();
    let project = Project::load(root);

    let findings = diagnose(&project, &healthy(root));

    // :core:data keeps the standard layout, and the root build script lacks what tech.annexflow.lvm.jvm needs.
    assert_eq!(
        rules(&findings),
        [("root-plugins", Severity::Error), ("flat-layout", Severity::Error), ("root-plugins", Severity::Warning)]
    );
    let fixes: Vec<_> = findings.iter().filter(|f| f.rule == "root-plugins").filter_map(|f| f.fix.clone()).collect();
    assert_eq!(
        fixes,
        [
            Fix::DeclareRootPlugins { lines: vec!["alias(lvmLibs.plugins.blueprint.jvm) apply false".into()] },
            Fix::DeclareRootPlugins { lines: vec!["alias(lvmLibs.plugins.kotlin.jvm) apply false".into()] },
        ]
    );
    replace(
        root,
        "build.gradle.kts",
        "plugins {\n",
        "plugins {\n    alias(libs.plugins.lvm.jvm) apply false\n    alias(libs.plugins.kotlin.jvm) apply false\n",
    );
    assert_eq!(rules(&diagnose(&Project::load(root), &healthy(root))), [("flat-layout", Severity::Error)]);
}

#[test]
fn only_notes_a_missing_test_runner_when_a_plugin_that_lvm_doesnt_know_may_add_it() {
    let dir = consumer();
    let root = dir.path();
    fs::create_dir_all(root.join("app/androidTest")).unwrap();
    fs::write(root.join("app/androidTest/AppTest.kt"), "class AppTest\n").unwrap();
    replace(
        root,
        "app/build.gradle.kts",
        "    alias(lvmLibs.plugins.blueprint.compose)\n",
        "    alias(lvmLibs.plugins.blueprint.compose)\n    alias(lvmLibs.plugins.kotlin.serialization)\n",
    );
    let test_runner = |root: &Path| {
        let findings = diagnose(&Project::load(root), &healthy(root));
        findings.iter().find(|f| f.rule == "test-runner").map(|f| f.severity)
    };

    assert_eq!(test_runner(root), Some(Severity::Warning));
    replace(root, "app/build.gradle.kts", "plugins {\n", "plugins {\n    id(\"com.example.android.convention\")\n");
    assert_eq!(test_runner(root), Some(Severity::Info));
}

#[test]
fn checks_the_machine_against_the_build() {
    let dir = consumer();
    let project = Project::load(dir.path());
    let env = Environment {
        os: "macos".into(),
        java: Some(17),
        android_sdk: None,
        xcode: Some(false),
        local_properties_tracked: true,
    };

    let findings = diagnose(&project, &env);

    let found = rules(&findings);
    for expected in [
        ("android-sdk", Severity::Error),
        ("local-properties", Severity::Error),
        ("xcode", Severity::Warning),
        ("jdk", Severity::Warning),
    ] {
        assert!(found.contains(&expected), "{expected:?} in {found:?}");
    }
    let mut changes = ChangeSet::default();
    findings
        .iter()
        .find(|f| f.rule == "local-properties")
        .unwrap()
        .fix
        .as_ref()
        .unwrap()
        .apply(&project, &mut changes)
        .unwrap();
    assert_eq!(changes.files[Path::new(".gitignore")].after, "local.properties\n");
    assert_eq!(changes.untrack, [Path::new("local.properties")]);
}

#[test]
fn ios_targets_off_macos_are_only_noted() {
    let dir = consumer();
    let project = Project::load(dir.path());
    let env = Environment { os: "linux".into(), ..healthy(dir.path()) };
    let findings = diagnose(&project, &env);
    assert!(rules(&findings).contains(&("xcode", Severity::Info)));
}

#[test]
fn suggests_keys_within_a_few_typos() {
    assert_eq!(suggest("android.minSDK").as_deref(), Some("android.minSdk"));
    assert_eq!(suggest("kotlin.optIn").as_deref(), Some("kotlin.optIns"));
    assert_eq!(suggest("something.else"), None);
}

#[test]
fn bugfix_releases_share_a_line() {
    assert_eq!(bugfix_line("2.4.21"), bugfix_line("2.4.20"));
    assert_ne!(bugfix_line("2.4.30"), bugfix_line("2.4.21"));
    assert_eq!(bugfix_line("2.4"), None);
}

#[test]
fn finds_secrets_in_the_build_and_incomplete_poms() {
    let dir = consumer();
    let root = dir.path();
    fs::write(
        root.join("gradle.properties"),
        "signingInMemoryKeyPassword=hunter2\nlvm.publish.url=https://example.com\n",
    )
    .unwrap();
    replace(
        root,
        "core/data/build.gradle.kts",
        "alias(lvmLibs.plugins.blueprint.multiplatform)",
        "alias(lvmLibs.plugins.blueprint.multiplatform)\n    alias(lvmLibs.plugins.blueprint.publish)",
    );
    let project = Project::load(root);

    let findings = diagnose(&project, &healthy(root));

    let secret = findings.iter().find(|f| f.rule == "publish-secrets").expect("a secret finding");
    assert_eq!(secret.line, Some(1));
    assert!(!secret.message.contains("hunter2"), "the message never shows the secret");
    let pom = findings.iter().find(|f| f.rule == "pom").expect("a pom finding");
    assert!(pom.message.contains("lvm.publish.description"), "{}", pom.message);
    assert!(!pom.message.contains("lvm.publish.url"), "{}", pom.message);
}
