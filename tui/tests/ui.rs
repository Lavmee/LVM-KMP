//! Renders the app on the consumer fixture and compares the screens with snapshots.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, Receiver};
use std::time::Duration;

use insta::assert_snapshot;
use lvm_tui::app::{App, Dialog, Tab};
use lvm_tui::applog::Level;
use lvm_tui::devices::{Device, Platform, State as DeviceState, Tools};
use lvm_tui::doctor::Environment;
use lvm_tui::event::Message;
use lvm_tui::gradle::{GradleEvent, Runner};
use lvm_tui::project::Project;
use lvm_tui::state::State;
use lvm_tui::ui;
use ratatui::Terminal;
use ratatui::backend::TestBackend;
use ratatui::crossterm::event::{Event, KeyCode, KeyEvent, KeyModifiers};

/// A copy of the fixture, so a test can add reports without touching the sources.
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
            copy(&entry.path(), &target)
        } else {
            fs::copy(entry.path(), target).unwrap();
        }
    }
}

fn app(root: &Path) -> App {
    let (sender, _) = mpsc::channel();
    App::new(Project::load(root), Runner::new(root, sender), &State::default())
}

/// The screen, with the temporary directory shown as `/work/consumer` and durations as `N` seconds, so snapshots
/// match on every machine.
fn render(app: &mut App) -> String {
    let mut terminal = Terminal::new(TestBackend::new(120, 36)).unwrap();
    terminal.draw(|frame| ui::draw(frame, app)).unwrap();
    let root = app.project.root.display().to_string();
    terminal.backend().to_string().lines().map(|line| seconds(&fixed_root(line, &root))).collect::<Vec<_>>().join("\n")
}

/// Replaces [root] in [line], keeping the text after it where it was.
fn fixed_root(line: &str, root: &str) -> String {
    const SHOWN: &str = "/work/consumer";
    let Some(start) = line.find(root) else { return line.to_owned() };
    let rest = &line[start + root.len()..];
    let spaces = rest.len() - rest.trim_start_matches(' ').len();
    let padding = (spaces + root.chars().count()).saturating_sub(SHOWN.len());
    format!("{}{SHOWN}{}{}", &line[..start], " ".repeat(padding), rest.trim_start_matches(' '))
}

/// `after 12s` -> `after Ns ` and `in 3s` -> `in Ns`, keeping the width of the line.
fn seconds(line: &str) -> String {
    let mut result = String::new();
    let mut rest = line;
    while let Some(at) = rest.find(|c: char| c.is_ascii_digit()) {
        let digits = rest[at..].chars().take_while(char::is_ascii_digit).count();
        let after = &rest[at + digits..];
        let before = &rest[..at];
        if after.starts_with('s') && (before.ends_with("after ") || before.ends_with("in ")) {
            result.push_str(before);
            result.push('N');
            result.push('s');
            let after = &after[1..];
            // Keep the width: the spaces the digits took go after the unit.
            result.push_str(&" ".repeat(digits - 1));
            rest = after;
        } else {
            result.push_str(&rest[..at + digits]);
            rest = after;
        }
    }
    result.push_str(rest);
    result
}

fn press(app: &mut App, keys: &str) {
    for c in keys.chars() {
        let code = match c {
            '\n' => KeyCode::Enter,
            c => KeyCode::Char(c),
        };
        app.update(Message::Input(Event::Key(KeyEvent::new(code, KeyModifiers::NONE))));
    }
}

fn ctrl(app: &mut App, c: char) {
    app.update(Message::Input(Event::Key(KeyEvent::new(KeyCode::Char(c), KeyModifiers::CONTROL))));
}

#[test]
fn overview_of_a_build_on_an_lvm_without_reports() {
    let dir = consumer();
    let mut app = app(dir.path());
    assert_snapshot!(render(&mut app));
}

#[test]
fn modules_show_values_with_their_sources() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2jj");
    assert_eq!(app.selected_module().map(|m| m.path.as_str()), Some(":shared"));
    assert_snapshot!(render(&mut app));
}

#[test]
fn the_filter_keeps_matching_values() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2jj/sdk\n");
    let keys: Vec<_> = app.visible_values().iter().map(|row| row.key.clone()).collect();
    assert_eq!(keys, ["android.compileSdk", "android.minSdk", "android.targetSdk"]);
    assert_snapshot!(render(&mut app));
}

#[test]
fn affected_modules_follow_dependencies_transitively() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2ja");
    assert_eq!(app.selected_module().map(|m| m.path.as_str()), Some(":core:data"));
    assert_eq!(app.project.affected(":core:data"), [":app", ":shared"]);
    assert_snapshot!(render(&mut app));
}

#[test]
fn the_palette_runs_the_command_it_finds() {
    let dir = consumer();
    let mut app = app(dir.path());
    ctrl(&mut app, 'p');
    press(&mut app, "modul");
    assert_snapshot!(render(&mut app));
    press(&mut app, "\n");
    assert!(app.palette.is_none());
    assert_eq!(app.tab, Tab::Modules);
}

#[test]
fn reports_replace_what_the_files_say() {
    let dir = consumer();
    let example = include_str!("../../blueprint/multiplatform/src/test/resources/lvm-report.json");
    let report =
        example.replace("\":lib\"", "\":shared\"").replace("\"projectDir\": \"lib\"", "\"projectDir\": \"shared\"");
    let file = dir.path().join("build/lvm/report/shared/project.json");
    fs::create_dir_all(file.parent().unwrap()).unwrap();
    fs::write(file, report).unwrap();

    let mut app = app(dir.path());
    assert_snapshot!("overview_with_a_report", render(&mut app));
    press(&mut app, "2jj");
    assert_snapshot!("module_from_a_report", render(&mut app));
}

#[test]
fn ctrl_c_quits_while_no_build_runs() {
    let dir = consumer();
    let mut app = app(dir.path());
    ctrl(&mut app, 'c');
    assert!(app.quit);
}

#[test]
fn the_state_remembers_the_tab_and_module() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2j");
    let state = app.state();
    assert_eq!(state.tab.as_deref(), Some("modules"));
    assert_eq!(state.module.as_deref(), Some(":core:data"));

    let (sender, _) = mpsc::channel();
    let restored = App::new(Project::load(dir.path()), Runner::new(dir.path(), sender), &state);
    assert_eq!(restored.tab, Tab::Modules);
    assert_eq!(restored.selected_module().map(|m| m.path.as_str()), Some(":core:data"));

    let library = State { tab: Some("library".to_owned()), ..State::default() };
    let (sender, _) = mpsc::channel();
    let restored = App::new(Project::load(dir.path()), Runner::new(dir.path(), sender), &library);
    assert_eq!(restored.tab, Tab::Overview, "no module publishes, so the build has no Library tab");
}

fn with_environment(app: &mut App, root: &Path) {
    let env = Environment {
        os: "macos".into(),
        java: Some(21),
        android_sdk: Some(root.to_owned()),
        xcode: Some(true),
        local_properties_tracked: false,
    };
    app.update(Message::Environment(env));
}

#[test]
fn the_doctor_fixes_a_finding_after_showing_the_change() {
    let dir = consumer();
    let mut app = app(dir.path());
    with_environment(&mut app, dir.path());
    press(&mut app, "5");
    assert_snapshot!("doctor_with_a_finding", render(&mut app));

    press(&mut app, "f");
    assert!(matches!(app.dialog, Some(Dialog::Confirm { .. })));
    assert_snapshot!("doctor_confirms_the_move", render(&mut app));

    press(&mut app, "y");
    assert!(matches!(app.dialog, Some(Dialog::OfferBuild)));
    press(&mut app, "n");
    assert!(dir.path().join("core/data/src/Repository.kt").is_file());
    assert!(app.findings.is_empty());
}

#[test]
fn editing_a_value_writes_it_to_the_chosen_layer() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2jj/minSdk\nl");
    press(&mut app, "j");
    press(&mut app, "e");
    assert_snapshot!("edit_dialog", render(&mut app));
    let Some(Dialog::Edit(edit)) = &app.dialog else { panic!("no edit dialog") };
    assert_eq!(edit.key, "android.minSdk");

    // The value comes from the settings script, so that is where it is edited first.
    app.update(Message::Input(Event::Key(KeyEvent::new(KeyCode::Backspace, KeyModifiers::NONE))));
    app.update(Message::Input(Event::Key(KeyEvent::new(KeyCode::Backspace, KeyModifiers::NONE))));
    press(&mut app, "x\n");
    let Some(Dialog::Edit(edit)) = &app.dialog else { panic!("the dialog closed on an invalid value") };
    assert_eq!(edit.error.as_deref(), Some("lvm.android.minSdk must be an integer, but was 'x'"));

    app.update(Message::Input(Event::Key(KeyEvent::new(KeyCode::Backspace, KeyModifiers::NONE))));
    press(&mut app, "28\n");
    assert!(matches!(app.dialog, Some(Dialog::Confirm { .. })));
    press(&mut app, "y");
    let settings = fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap();
    assert!(settings.contains("android { minSdk = 28 }"), "{settings}");
    let shared = app.project.module(":shared").unwrap();
    let min_sdk = shared.values.iter().find(|row| row.key == "android.minSdk").unwrap();
    assert_eq!(min_sdk.value, Some(28.into()));
}

#[test]
fn a_module_moves_to_the_flat_layout_from_the_modules_tab() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2jm");
    let Some(Dialog::Confirm { preview, .. }) = &app.dialog else { panic!("no confirmation") };
    assert!(
        preview.contains(
            &"move core/data/src/commonMain/kotlin/com/example/data/Repository.kt -> core/data/src/Repository.kt"
                .to_owned()
        ),
        "{preview:?}"
    );
    press(&mut app, "n");
    assert!(app.dialog.is_none());
    assert!(dir.path().join("core/data/src/commonMain/kotlin/com/example/data/Repository.kt").is_file());
}

/// An app whose Gradle is [script], a shell script, with the channel its events arrive on.
#[cfg(unix)]
fn app_with_gradle(root: &Path, script: &str) -> (App, Receiver<Message>) {
    use std::os::unix::fs::PermissionsExt;
    let gradle = root.join("fake-gradle");
    fs::write(&gradle, format!("#!/bin/sh\n{script}\n")).unwrap();
    fs::set_permissions(&gradle, fs::Permissions::from_mode(0o755)).unwrap();
    let (sender, receiver) = mpsc::channel();
    let app = App::new(Project::load(root), Runner::new(root, sender).with_executable(gradle), &State::default());
    (app, receiver)
}

/// Hands the app every event of the running build until it finishes.
fn until_finished(app: &mut App, events: &Receiver<Message>) {
    loop {
        let message = events.recv_timeout(Duration::from_secs(10)).expect("an event");
        let done = matches!(message, Message::Gradle(GradleEvent::Finished { .. }));
        app.update(message);
        if done {
            return;
        }
    }
}

const FAILED_TEST: &str = r#"<testsuite name="com.example.shared.GreetingTest">
  <testcase name="greets" classname="com.example.shared.GreetingTest" time="0.02">
    <failure message="expected:&lt;Hi&gt; but was:&lt;Hello&gt;">java.lang.AssertionError: expected:&lt;Hi&gt; but was:&lt;Hello&gt;
	at com.example.shared.GreetingTest.greets(GreetingTest.kt:7)
</failure>
  </testcase>
  <testcase name="waves" classname="com.example.shared.GreetingTest" time="0.01"/>
</testsuite>"#;

#[cfg(unix)]
#[test]
fn a_task_runs_and_its_log_points_at_the_error() {
    let dir = consumer();
    let script = "echo '> Task :shared:compileKotlinJvm'\necho 'e: shared/src/Greeting.kt:3:5 Unresolved reference'\necho 'BUILD FAILED in 1s'\nexit 1";
    let (mut app, events) = app_with_gradle(dir.path(), script);

    // After the whole build come :app, :core:data and :shared, whose tasks start with build and check.
    press(&mut app, "3jjjl");
    assert_eq!(app.visible_tasks()[..2], ["build".to_owned(), "check".to_owned()]);
    press(&mut app, "\n");
    until_finished(&mut app, &events);

    assert_eq!(app.runs.len(), 1);
    assert_eq!(app.runs[0].command, "./gradlew :shared:build");
    press(&mut app, "ln");
    assert_eq!(app.tasks.cursor, Some(1));
    assert_snapshot!("tasks_log_with_an_error", render(&mut app));
    press(&mut app, "o");
    let request = app.editor.take().expect("an editor request");
    assert_eq!(request.file, dir.path().join("shared/src/Greeting.kt"));
    assert_eq!(request.line, Some(3));
}

#[cfg(unix)]
#[test]
fn failed_tests_open_at_their_frame_and_run_again() {
    let dir = consumer();
    let results = dir.path().join("shared/build/test-results/jvmTest");
    fs::create_dir_all(&results).unwrap();
    fs::write(results.join("TEST-com.example.shared.GreetingTest.xml"), FAILED_TEST).unwrap();
    fs::create_dir_all(dir.path().join("shared/test")).unwrap();
    fs::write(dir.path().join("shared/test/GreetingTest.kt"), "package com.example.shared\n").unwrap();
    let (mut app, events) = app_with_gradle(dir.path(), "echo \"args: $*\"");

    press(&mut app, "3lv");
    assert_snapshot!("tasks_tests_with_a_failure", render(&mut app));
    press(&mut app, "o");
    let request = app.editor.take().expect("an editor request");
    assert!(request.file.ends_with("shared/test/GreetingTest.kt"), "{}", request.file.display());
    assert_eq!(request.line, Some(7));

    press(&mut app, "a");
    until_finished(&mut app, &events);
    let log: Vec<&String> = app.runs[0].lines.iter().collect();
    assert_eq!(log, ["args: :shared:jvmTest --tests com.example.shared.GreetingTest.greets --console=plain"]);
}

#[test]
fn a_typed_command_keeps_the_keys_of_the_app_for_itself() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "3jjjc");
    assert_eq!(app.tasks.command.as_deref(), Some(":shared:"));
    press(&mut app, "q1");
    assert!(!app.quit);
    assert_eq!(app.tasks.command.as_deref(), Some(":shared:q1"));
}

fn releases(app: &mut App) {
    app.fetch_releases = false;
    app.update(Message::Releases(Ok(vec!["0.4.0".into(), "0.3.2".into(), "0.3.1".into()])));
    for (version, kotlin, agp) in [("0.3.2", "2.4.21", "9.5.1"), ("0.4.0", "2.5.0", "9.6.0")] {
        let catalog = [("kotlin", kotlin), ("agp", agp), ("composeMultiplatform", "1.12.2")]
            .into_iter()
            .map(|(name, version)| (name.to_owned(), version.to_owned()))
            .collect();
        app.update(Message::Catalog { version: version.into(), catalog: Ok(catalog) });
    }
}

#[test]
fn updates_compare_a_release_and_upgrade_to_it() {
    let dir = consumer();
    let mut app = app(dir.path());
    releases(&mut app);
    press(&mut app, "6");
    assert_eq!(app.updates.selected_version(), Some("0.4.0"));
    assert_snapshot!("updates_to_the_latest", render(&mut app));

    press(&mut app, "u");
    let Some(Dialog::Confirm { preview, .. }) = &app.dialog else { panic!("no confirmation") };
    assert!(preview.contains(&"+    id(\"tech.annexflow.lvm.settings\") version \"0.4.0\"".to_owned()), "{preview:?}");
    press(&mut app, "yn");
    let settings = fs::read_to_string(dir.path().join("settings.gradle.kts")).unwrap();
    assert!(settings.contains("version \"0.4.0\""), "{settings}");
    assert_eq!(app.project.lvm, lvm_tui::project::model::LvmVersion::Pinned("0.4.0".into()));
}

#[test]
fn updates_say_when_maven_central_cannot_be_reached() {
    let dir = consumer();
    let mut app = app(dir.path());
    app.fetch_releases = false;
    app.update(Message::Releases(Err("connection refused".into())));
    press(&mut app, "6");
    let screen = render(&mut app);
    assert!(screen.contains("Maven Central can't be reached"), "{screen}");
    press(&mut app, "u");
    assert!(app.dialog.is_none());
}

#[test]
fn a_module_is_created_from_the_form() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2n");
    press(&mut app, "feature:chat");
    assert_snapshot!("new_module_form", render(&mut app));
    press(&mut app, "\n");
    let Some(Dialog::Confirm { title, .. }) = &app.dialog else { panic!("no confirmation") };
    assert_eq!(title, "Create :feature:chat");
    press(&mut app, "yn");

    assert!(dir.path().join("feature/chat/build.gradle.kts").is_file());
    assert!(dir.path().join("feature/chat/src/Chat.kt").is_file());
    assert!(app.project.module(":feature:chat").is_some());
}

#[test]
fn the_module_form_reports_what_is_wrong() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2n");
    press(&mut app, "shared\n");
    let Some(Dialog::Form { form, .. }) = &app.dialog else { panic!("the form closed") };
    assert_eq!(form.error.as_deref(), Some("the build already has :shared"));
}

#[test]
fn a_library_is_added_with_what_it_needs() {
    let dir = consumer();
    let mut app = app(dir.path());
    press(&mut app, "2jd");
    press(&mut app, "storage\n");
    let Some(Dialog::Confirm { preview, .. }) = &app.dialog else { panic!("no confirmation") };
    assert!(preview[0].starts_with("note: lvm-storage stores @Serializable values"), "{preview:?}");
    press(&mut app, "yn");

    let script = fs::read_to_string(dir.path().join("core/data/build.gradle.kts")).unwrap();
    assert!(script.contains("implementation(lvmLibs.lvm.storage)"), "{script}");
    assert!(script.contains("alias(lvmLibs.plugins.kotlin.serialization)"), "{script}");
}

/// The fixture with `:core:data` published, part of its POM set and its ABI changed since the dump.
fn published_consumer() -> tempfile::TempDir {
    let dir = consumer();
    let root = dir.path();
    let script = root.join("core/data/build.gradle.kts");
    let text = fs::read_to_string(&script).unwrap().replace(
        "alias(lvmLibs.plugins.blueprint.multiplatform)",
        "alias(lvmLibs.plugins.blueprint.multiplatform)\n    alias(lvmLibs.plugins.blueprint.publish)",
    );
    fs::write(script, text).unwrap();
    fs::write(
        root.join("gradle.properties"),
        "lvm.publish.description=Data\nlvm.publish.url=https://example.com/data\n",
    )
    .unwrap();
    fs::create_dir_all(root.join("core/data/api")).unwrap();
    fs::write(root.join("core/data/api/data.api"), "public final class Repository {\n\tpublic fun load ()V\n}\n")
        .unwrap();
    dir
}

#[cfg(unix)]
#[test]
fn the_library_checks_the_abi_and_shows_what_changed() {
    let dir = published_consumer();
    let generated = "mkdir -p core/data/build/kotlin/abi\nprintf 'public final class Repository {\\n\\tpublic fun save ()V\\n}\\n' > core/data/build/kotlin/abi/data.api\nexit 1";
    let (mut app, events) = app_with_gradle(dir.path(), generated);
    let home = tempfile::tempdir().unwrap();
    fs::write(home.path().join("gradle.properties"), "mavenCentralUsername=me\nmavenCentralPassword=secret\n").unwrap();
    app.library.gradle_home = Some(home.path().to_owned());

    assert_eq!(app.tabs().get(6), Some(&Tab::Library));
    press(&mut app, "7");
    assert_eq!(app.tab, Tab::Library);
    press(&mut app, "c");
    until_finished(&mut app, &events);
    assert_eq!(app.runs[0].command, "./gradlew :core:data:checkKotlinAbi");
    let lvm_tui::library::AbiState::Changed(diffs) = &app.library.abi else { panic!("the ABI changed") };
    assert_eq!((diffs[0].added, diffs[0].removed), (1, 1));
    assert_snapshot!("library_with_a_changed_abi", render(&mut app));
}

#[cfg(unix)]
#[test]
fn the_library_publishes_to_maven_local_without_signing() {
    let dir = published_consumer();
    let (mut app, events) = app_with_gradle(dir.path(), "echo \"$*\"");
    press(&mut app, "7p");
    until_finished(&mut app, &events);
    assert_eq!(app.runs[0].lines[0], ":core:data:publishToMavenLocal -Plvm.publish.signing=false --console=plain");
}

#[test]
fn the_library_tab_exists_only_where_a_module_publishes() {
    let dir = consumer();
    let mut app = app(dir.path());
    assert!(!app.tabs().contains(&Tab::Library));
    press(&mut app, "8");
    assert_eq!(app.tab, Tab::Overview, "7 is the last tab, Compose");
}

/// An emulator that runs, an AVD that doesn't, and a simulator.
fn devices(adb: Option<PathBuf>) -> Message {
    let device =
        |platform, id: &str, name: &str, state, avd| Device { platform, id: id.into(), name: name.into(), state, avd };
    Message::Devices {
        tools: Tools { adb, emulator: None, simctl: false },
        devices: vec![
            device(Platform::Android, "emulator-5554", "Pixel 8 (emulator-5554)", DeviceState::Ready, false),
            device(Platform::Android, "Pixel_9", "Pixel 9 (AVD)", DeviceState::Stopped, true),
            device(Platform::Ios, "A1B2", "iPhone 18 (iOS 27.0)", DeviceState::Ready, false),
        ],
    }
}

/// A shell script at [path] that prints its name and arguments.
#[cfg(unix)]
fn fake_tool(path: &Path) {
    use std::os::unix::fs::PermissionsExt;
    let name = path.file_name().unwrap().to_string_lossy();
    fs::write(path, format!("#!/bin/sh\necho \"{name} $*\"\n")).unwrap();
    fs::set_permissions(path, fs::Permissions::from_mode(0o755)).unwrap();
}

#[test]
fn the_run_tab_lists_apps_and_the_devices_of_their_platform() {
    let dir = consumer();
    let mut app = app(dir.path());
    app.run.probe = false;
    press(&mut app, "4");
    assert_eq!(app.tab, Tab::Run);
    app.update(devices(Some(PathBuf::from("/sdk/platform-tools/adb"))));
    assert_snapshot!("run_with_devices", render(&mut app));

    press(&mut app, "lj\n");
    assert!(app.runs.is_empty(), "the AVD isn't running");
    // Windows has no shell for the steps of an Android run, so it says that first.
    if cfg!(unix) {
        assert_eq!(app.status.as_deref(), Some("Pixel 9 (AVD) isn't running: s starts it"));
    }
    press(&mut app, "s");
    assert_eq!(app.status.as_deref(), Some("No emulator in the Android SDK"));
    // The iOS framework builds for the simulator, with no device to pick.
    press(&mut app, "hj");
    assert_eq!(app.selected_device(), None);
}

#[test]
fn the_run_tab_selects_the_device_of_the_last_run() {
    let dir = consumer();
    let (sender, _) = mpsc::channel();
    let state = State { device: Some("Pixel_9".to_owned()), ..State::default() };
    let mut app = App::new(Project::load(dir.path()), Runner::new(dir.path(), sender), &state);
    app.run.probe = false;
    press(&mut app, "4");
    app.update(devices(None));
    assert_eq!(app.selected_device().map(|d| d.id), Some("Pixel_9".to_owned()));
    assert_eq!(app.state().device.as_deref(), Some("Pixel_9"));
}

#[cfg(unix)]
#[test]
fn an_android_app_installs_on_the_chosen_device_and_starts() {
    let dir = consumer();
    let (mut app, events) = app_with_gradle(dir.path(), "echo \"gradle $ANDROID_SERIAL $*\"");
    app.run.probe = false;
    let adb = dir.path().join("adb");
    fake_tool(&adb);
    press(&mut app, "4");
    app.update(devices(Some(adb)));
    press(&mut app, "\n");
    until_finished(&mut app, &events);

    let run = &app.runs[0];
    assert_eq!(run.command, "Run :app on Pixel 8 (emulator-5554)");
    assert_eq!(run.lines[0], "gradle emulator-5554 :app:installDebug --console=plain");
    assert_eq!(
        run.lines[1],
        "adb -s emulator-5554 shell am start -n $(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.app | tail -n 1)"
    );
    assert_eq!(app.state().device.as_deref(), Some("emulator-5554"));
}

#[cfg(unix)]
#[test]
fn the_log_follows_a_file_by_level_and_search() {
    let dir = consumer();
    let (mut app, events) = app_with_gradle(dir.path(), "true");
    app.run.probe = false;
    let log = dir.path().join("app.log");
    fs::write(
        &log,
        "2026-11-18T10:15:31.861+03:00 Info: (Net) GET /items\n\
         2026-11-18T10:15:32.002+03:00 Warn: (Net) slow response\n\
         2026-11-18T10:15:32.100+03:00 Error: (Storage) disk full\n",
    )
    .unwrap();
    press(&mut app, "4F");
    press(&mut app, &format!("{}\n", log.display()));
    let mut lines = 0;
    while lines < 3 {
        let message = events.recv_timeout(Duration::from_secs(10)).expect("a line of the log");
        lines += usize::from(matches!(message, Message::AppLog { .. }));
        app.update(message);
    }

    press(&mut app, "LLL");
    assert_eq!(app.run.visible().len(), 2, "warnings and errors");
    press(&mut app, "/net\n");
    assert_eq!(app.run.visible().len(), 1);
    assert_snapshot!("run_following_a_log_file", render(&mut app));
    press(&mut app, "x");
    assert_eq!(app.run.ended.as_deref(), Some("Stopped"));
}

#[cfg(unix)]
#[test]
fn a_jvm_app_s_output_is_its_log() {
    let dir = consumer();
    let script = dir.path().join("core/data/build.gradle.kts");
    let text = fs::read_to_string(&script).unwrap() + "\ncompose.desktop { application { mainClass = \"MainKt\" } }\n";
    fs::write(script, text).unwrap();
    let (mut app, events) =
        app_with_gradle(dir.path(), "echo \"Info: (App) started by $1\"\necho 'Error: (App) crashed'");
    app.run.probe = false;
    press(&mut app, "4");
    let jvm = app.run.runnables.iter().position(|r| r.platform == Platform::Jvm).expect("a JVM app");
    app.run.targets.select(Some(jvm));
    press(&mut app, "\n");
    until_finished(&mut app, &events);

    let messages: Vec<_> = app.run.log.iter().map(|l| (l.level, l.tag.as_str(), l.message.as_str())).collect();
    assert_eq!(
        messages,
        [(Level::Info, "App", "started by :core:data:run"), (Level::Error, "App", "crashed")],
        "the run ended with {:?}",
        app.run.ended
    );
    assert_eq!(app.run.source.as_deref(), Some("output of :core:data:run"));
    assert_eq!(app.run.ended.as_deref(), Some("The app exited"));
}

const COMPOSE_CLASSES: &str = "stable class com.example.shared.Item {
  stable val title: String
  <runtime stability> = Stable
}
unstable class com.example.shared.FeedState {
  unstable var items: MutableList<Item>
  <runtime stability> = Unstable
}
";

const COMPOSE_COMPOSABLES: &str = r#"restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.example.shared.Feed(
  state: FeedState
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.example.shared.Row(
  item: Item
)
"#;

const COMPOSE_METRICS: &str = r#"{"totalComposables": 2, "restartableComposables": 2, "skippableComposables": 2,
  "totalClasses": 2, "inferredUnstableClasses": 1, "inferredUncertainClasses": 0, "featureFlags": {"StrongSkipping": true}}"#;

/// Writes the Compose reports of `:shared` as the compiler would, and the source they describe.
fn compose_reports(root: &Path) {
    let reports = root.join("shared/build/compose-reports");
    fs::create_dir_all(&reports).unwrap();
    fs::write(reports.join("shared-classes.txt"), COMPOSE_CLASSES).unwrap();
    fs::write(reports.join("shared-composables.txt"), COMPOSE_COMPOSABLES).unwrap();
    let metrics = root.join("shared/build/compose-metrics/jvm/main");
    fs::create_dir_all(&metrics).unwrap();
    fs::write(metrics.join("shared-module.json"), COMPOSE_METRICS).unwrap();
    fs::write(
        root.join("shared/src/Feed.kt"),
        "package com.example.shared\n\nclass FeedState(var items: MutableList<Item>)\n\nclass Item(val title: String)\n\n@Composable\nfun Feed(state: FeedState, modifier: Modifier = Modifier) {}\n",
    )
    .unwrap();
}

#[test]
fn the_compose_tab_shows_what_the_reports_say_and_opens_it() {
    let dir = consumer();
    compose_reports(dir.path());
    let mut app = app(dir.path());
    press(&mut app, "7j");
    assert_eq!(app.tab, Tab::Compose);
    assert_snapshot!("compose_with_findings", render(&mut app));

    press(&mut app, "lj\n");
    let editor = app.editor.clone().expect("the declaration to open");
    assert_eq!((editor.file, editor.line), (dir.path().join("shared/src/Feed.kt"), Some(8)));
}

#[cfg(unix)]
#[test]
fn compose_reports_come_from_one_compilation_built_again() {
    let dir = consumer();
    let script = "echo \"$*\"\nmkdir -p shared/build/compose-reports\nprintf 'unstable class com.example.shared.A {\\n}\\n' > shared/build/compose-reports/shared-classes.txt";
    let (mut app, events) = app_with_gradle(dir.path(), script);
    press(&mut app, "7");
    assert!(app.compose.report.is_empty());
    assert_snapshot!("compose_without_reports", render(&mut app));
    press(&mut app, "jc");
    until_finished(&mut app, &events);

    assert_eq!(
        app.runs[0].lines[0],
        ":shared:compileKotlinJvm --rerun -Plvm.compose.reports=true -Plvm.compose.metrics=true --console=plain"
    );
    assert_eq!(app.compose.report.classes.len(), 1);
}
