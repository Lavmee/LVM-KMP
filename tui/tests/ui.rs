//! Renders the app on the consumer fixture and compares the screens with snapshots.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::mpsc;

use insta::assert_snapshot;
use lvm_tui::app::{App, Tab};
use lvm_tui::event::Message;
use lvm_tui::gradle::Runner;
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
    let mut project = Project::load(root);
    // The temporary directory differs on every run; snapshots show a fixed one.
    project.root = PathBuf::from("/work/consumer");
    let (sender, _) = mpsc::channel();
    App::new(project, Runner::new(root, sender), &State::default())
}

fn render(app: &mut App) -> String {
    let mut terminal = Terminal::new(TestBackend::new(120, 36)).unwrap();
    terminal.draw(|frame| ui::draw(frame, app)).unwrap();
    terminal.backend().to_string()
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
}
