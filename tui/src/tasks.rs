//! The Tasks tab: running Gradle tasks of the build or of a module, and reading what they leave behind.

use std::collections::BTreeMap;
use std::path::PathBuf;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::widgets::ListState;

use crate::app::{App, EditorRequest};
use crate::gradle::Purpose;
use crate::project::model::Module;
use crate::results::junit::{Status, TestCase};
use crate::results::{self, Results, log};

/// Tasks worth having at hand, in this order, before every other task of a module.
const POPULAR: &[&str] = &[
    "build",
    "check",
    "assemble",
    "allTests",
    "jvmTest",
    "test",
    "testDebugUnitTest",
    "testAndroidHostTest",
    "iosSimulatorArm64Test",
    "detekt",
    "checkKotlinAbi",
    "updateKotlinAbi",
    "assembleDebug",
    "installDebug",
    "run",
    "jvmRun",
    "lvmReport",
    "clean",
];

/// What `gradlew <task>` runs in every project of the build.
const BUILD_TASKS: &[&str] =
    &["build", "check", "assemble", "allTests", "detekt", "checkKotlinAbi", "lvmReport", "clean"];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TasksFocus {
    Targets,
    Tasks,
    Results,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ResultsPane {
    Log,
    Tests,
    Detekt,
    History,
}

impl ResultsPane {
    pub const ALL: [ResultsPane; 4] = [ResultsPane::Log, ResultsPane::Tests, ResultsPane::Detekt, ResultsPane::History];
}

pub struct TasksView {
    pub focus: TasksFocus,
    /// The first entry is the whole build, the others the modules.
    pub targets: ListState,
    pub tasks: ListState,
    pub filter: String,
    pub filtering: bool,
    pub pane: ResultsPane,
    /// The run the log shows, by its index in the history; the latest while `None`.
    pub run: Option<usize>,
    pub scroll: usize,
    /// Keeps the log at its end while lines arrive.
    pub follow: bool,
    /// The line of the log that `n` and `N` moved to, which `o` opens.
    pub cursor: Option<usize>,
    pub tests: ListState,
    pub issues: ListState,
    pub history: ListState,
    /// A command line being typed after `c`.
    pub command: Option<String>,
}

impl Default for TasksView {
    fn default() -> Self {
        Self {
            focus: TasksFocus::Targets,
            targets: ListState::default().with_selected(Some(0)),
            tasks: ListState::default().with_selected(Some(0)),
            filter: String::new(),
            filtering: false,
            pane: ResultsPane::Log,
            run: None,
            scroll: 0,
            follow: true,
            cursor: None,
            tests: ListState::default(),
            issues: ListState::default(),
            history: ListState::default(),
            command: None,
        }
    }
}

/// The tasks of [module] that lvm-tui offers: those of its report, or those its plugins add, popular ones first.
pub fn module_tasks(module: &Module) -> Vec<String> {
    let mut tasks: Vec<String> = if module.tasks.is_empty() { plugin_tasks(module) } else { module.tasks.clone() };
    let rank = |task: &str| {
        POPULAR
            .iter()
            .position(|p| *p == task)
            .unwrap_or_else(|| if task.starts_with("linkDebugFramework") { POPULAR.len() } else { usize::MAX })
    };
    tasks.sort_by(|a, b| (rank(a), a).cmp(&(rank(b), b)));
    tasks.dedup();
    tasks
}

/// The tasks a module surely has, by its plugins and targets, when it has no report that lists them.
fn plugin_tasks(module: &Module) -> Vec<String> {
    let mut tasks = vec!["build", "check", "assemble", "clean"];
    let has = |target: &str| module.targets.iter().any(|t| t == target);
    if module.has_plugin("multiplatform") {
        tasks.push("allTests");
        if has("jvm") {
            tasks.push("jvmTest");
        }
        if has("android") {
            tasks.push("testAndroidHostTest");
        }
        if has("ios") {
            tasks.push("iosSimulatorArm64Test");
        }
    }
    if module.has_plugin("android.application") {
        tasks.extend(["assembleDebug", "installDebug", "testDebugUnitTest"]);
    }
    if module.has_plugin("android.library") {
        tasks.push("testDebugUnitTest");
    }
    if module.has_plugin("quality") {
        tasks.push("detekt");
    }
    if module.has_plugin("publish") {
        tasks.extend(["checkKotlinAbi", "updateKotlinAbi"]);
    }
    if module.is_lvm() {
        tasks.push("lvmReport");
    }
    tasks.into_iter().map(str::to_owned).collect()
}

/// Splits a command line into arguments, keeping quoted text together: `--tests "a b"` -> `--tests`, `a b`.
pub fn split_args(line: &str) -> Vec<String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut quote: Option<char> = None;
    let mut started = false;
    for c in line.chars() {
        match (quote, c) {
            (Some(q), c) if c == q => quote = None,
            (Some(_), c) => current.push(c),
            (None, '"' | '\'') => {
                quote = Some(c);
                started = true;
            }
            (None, c) if c.is_whitespace() => {
                if started {
                    args.push(std::mem::take(&mut current));
                    started = false;
                }
            }
            (None, c) => {
                current.push(c);
                started = true;
            }
        }
    }
    if started {
        args.push(current);
    }
    args
}

/// The Gradle arguments that run [tests] again: each test task with a `--tests` filter per test.
pub fn rerun_args<'a>(tests: impl Iterator<Item = &'a TestCase>) -> Vec<String> {
    let mut by_task: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for test in tests {
        let task =
            if test.module == ":" { format!(":{}", test.task) } else { format!("{}:{}", test.module, test.task) };
        by_task.entry(task).or_default().push(test.filter());
    }
    by_task
        .into_iter()
        .flat_map(|(task, filters)| {
            std::iter::once(task).chain(filters.into_iter().flat_map(|filter| ["--tests".to_owned(), filter]))
        })
        .collect()
}

impl App {
    /// The targets of the Tasks tab: `None` for the whole build, then each module.
    pub fn task_targets(&self) -> Vec<Option<&Module>> {
        std::iter::once(None).chain(self.project.modules.iter().map(Some)).collect()
    }

    pub fn selected_target(&self) -> Option<&Module> {
        self.task_targets().get(self.tasks.targets.selected().unwrap_or(0)).copied().flatten()
    }

    /// The tasks of the selected target that the filter keeps.
    pub fn visible_tasks(&self) -> Vec<String> {
        let tasks = match self.selected_target() {
            Some(module) => module_tasks(module),
            None => BUILD_TASKS.iter().map(|t| (*t).to_owned()).collect(),
        };
        let filter = self.tasks.filter.to_lowercase();
        tasks.into_iter().filter(|task| filter.is_empty() || task.to_lowercase().contains(&filter)).collect()
    }

    /// Runs [args] in the background and shows its log.
    pub fn run_gradle(&mut self, args: Vec<String>) {
        self.runner.submit(args, Purpose::Task);
        self.tasks.pane = ResultsPane::Log;
        self.tasks.run = None;
        self.tasks.follow = true;
        self.tasks.cursor = None;
        self.status = None;
    }

    pub(crate) fn on_tasks_key(&mut self, key: KeyEvent) {
        if self.tasks.command.is_some() {
            self.on_command_key(key);
            return;
        }
        if self.tasks.filtering {
            match key.code {
                KeyCode::Enter => self.tasks.filtering = false,
                KeyCode::Esc => {
                    self.tasks.filtering = false;
                    self.tasks.filter.clear();
                }
                KeyCode::Backspace => {
                    self.tasks.filter.pop();
                }
                KeyCode::Char(c) => self.tasks.filter.push(c),
                _ => {}
            }
            self.tasks.tasks.select(Some(0));
            return;
        }
        match key.code {
            KeyCode::Char('h') | KeyCode::Left => {
                self.tasks.focus = match self.tasks.focus {
                    TasksFocus::Results => TasksFocus::Tasks,
                    _ => TasksFocus::Targets,
                };
            }
            KeyCode::Char('l') | KeyCode::Right => {
                self.tasks.focus = match self.tasks.focus {
                    TasksFocus::Targets => TasksFocus::Tasks,
                    _ => TasksFocus::Results,
                };
            }
            KeyCode::Char('v') => {
                let position = ResultsPane::ALL.iter().position(|p| *p == self.tasks.pane).unwrap_or(0);
                self.tasks.pane = ResultsPane::ALL[(position + 1) % ResultsPane::ALL.len()];
                self.tasks.focus = TasksFocus::Results;
            }
            KeyCode::Char('c') => {
                let prefix = match self.selected_target() {
                    Some(module) if module.path != ":" => format!("{}:", module.path),
                    _ => String::new(),
                };
                self.tasks.command = Some(prefix);
            }
            KeyCode::Char('/') if self.tasks.focus != TasksFocus::Results => {
                self.tasks.focus = TasksFocus::Tasks;
                self.tasks.filtering = true;
            }
            KeyCode::Char('x') => self.perform(crate::app::Action::Cancel),
            _ => match self.tasks.focus {
                TasksFocus::Targets => self.on_targets_key(key),
                TasksFocus::Tasks => self.on_task_list_key(key),
                TasksFocus::Results => self.on_results_key(key),
            },
        }
    }

    fn on_command_key(&mut self, key: KeyEvent) {
        let Some(command) = self.tasks.command.as_mut() else { return };
        match key.code {
            KeyCode::Esc => self.tasks.command = None,
            KeyCode::Enter => {
                let args = split_args(command);
                self.tasks.command = None;
                if !args.is_empty() {
                    self.run_gradle(args);
                }
            }
            KeyCode::Backspace => {
                command.pop();
            }
            KeyCode::Char(c) => command.push(c),
            _ => {}
        }
    }

    fn on_targets_key(&mut self, key: KeyEvent) {
        let count = self.task_targets().len();
        if move_in(&mut self.tasks.targets, count, key.code) {
            self.tasks.tasks.select(Some(0));
        } else if key.code == KeyCode::Enter {
            self.tasks.focus = TasksFocus::Tasks;
        }
    }

    fn on_task_list_key(&mut self, key: KeyEvent) {
        let tasks = self.visible_tasks();
        if move_in(&mut self.tasks.tasks, tasks.len(), key.code) {
            return;
        }
        if key.code == KeyCode::Enter
            && let Some(task) = self.tasks.tasks.selected().and_then(|i| tasks.get(i))
        {
            let path = match self.selected_target() {
                Some(module) if module.path == ":" => format!(":{task}"),
                Some(module) => format!("{}:{task}", module.path),
                None => task.clone(),
            };
            self.run_gradle(vec![path]);
        }
    }

    fn on_results_key(&mut self, key: KeyEvent) {
        match self.tasks.pane {
            ResultsPane::Log => self.on_log_key(key),
            ResultsPane::Tests => {
                let count = self.results.tests.len();
                if move_in(&mut self.tasks.tests, count, key.code) {
                    return;
                }
                let selected = self.tasks.tests.selected().and_then(|i| self.results.tests.get(i)).cloned();
                match key.code {
                    KeyCode::Enter | KeyCode::Char('o') => {
                        if let Some(test) = &selected {
                            self.open_test(test);
                        }
                    }
                    KeyCode::Char('t') => {
                        if let Some(test) = selected {
                            self.run_gradle(rerun_args(std::iter::once(&test)));
                        }
                    }
                    KeyCode::Char('a') => {
                        let args = rerun_args(self.results.failed());
                        if args.is_empty() {
                            self.status = Some("No test failed".to_owned());
                        } else {
                            self.run_gradle(args);
                        }
                    }
                    _ => {}
                }
            }
            ResultsPane::Detekt => {
                let count = self.results.issues.len();
                if move_in(&mut self.tasks.issues, count, key.code) {
                    return;
                }
                if matches!(key.code, KeyCode::Enter | KeyCode::Char('o'))
                    && let Some(issue) = self.tasks.issues.selected().and_then(|i| self.results.issues.get(i))
                {
                    self.editor = Some(EditorRequest { file: issue.file.clone(), line: Some(issue.line) });
                }
            }
            ResultsPane::History => {
                let count = self.runs.len();
                if move_in(&mut self.tasks.history, count, key.code) {
                    return;
                }
                if key.code == KeyCode::Enter
                    && let Some(index) = self.tasks.history.selected()
                {
                    // The history lists the newest run first.
                    self.tasks.run = Some(self.runs.len() - 1 - index);
                    self.tasks.pane = ResultsPane::Log;
                    self.tasks.follow = false;
                    self.tasks.scroll = 0;
                    self.tasks.cursor = None;
                }
            }
        }
    }

    /// The lines of the run that the log shows.
    pub fn log_lines(&self) -> Vec<&str> {
        let run = match self.tasks.run {
            Some(index) => self.runs.get(index),
            None => self.runs.last(),
        };
        run.map(|run| run.lines.iter().map(String::as_str).collect()).unwrap_or_default()
    }

    fn on_log_key(&mut self, key: KeyEvent) {
        let lines = self.log_lines();
        let count = lines.len();
        let problems: Vec<usize> =
            lines.iter().enumerate().filter(|(_, l)| log::is_problem(l)).map(|(i, _)| i).collect();
        let cursor = self.tasks.cursor;
        let location =
            cursor.or_else(|| problems.first().copied()).and_then(|i| lines.get(i)).and_then(|l| log::location(l));
        match key.code {
            KeyCode::Down | KeyCode::Char('j') => self.scroll_log(1, count),
            KeyCode::Up | KeyCode::Char('k') => self.scroll_log(-1, count),
            KeyCode::PageDown | KeyCode::Char(' ') => self.scroll_log(20, count),
            KeyCode::PageUp => self.scroll_log(-20, count),
            KeyCode::Char('g') => self.scroll_log(isize::MIN / 2, count),
            KeyCode::Char('G') => {
                self.tasks.follow = true;
                self.tasks.scroll = count.saturating_sub(1);
            }
            KeyCode::Char('f') => self.tasks.follow = !self.tasks.follow,
            KeyCode::Char('n') => {
                let next =
                    problems.iter().copied().find(|&i| cursor.is_none_or(|c| i > c)).or(problems.first().copied());
                self.jump_to(next);
            }
            KeyCode::Char('N') => {
                let previous =
                    problems.iter().rev().copied().find(|&i| cursor.is_none_or(|c| i < c)).or(problems.last().copied());
                self.jump_to(previous);
            }
            KeyCode::Char('o') | KeyCode::Enter => match location {
                Some((file, line)) => self.open_location(file, line, None),
                None => self.status = Some("No file in this line: n moves to the next error".to_owned()),
            },
            _ => {}
        }
    }

    fn scroll_log(&mut self, delta: isize, count: usize) {
        self.tasks.follow = false;
        let next = (self.tasks.scroll as isize).saturating_add(delta).clamp(0, count.saturating_sub(1) as isize);
        self.tasks.scroll = next as usize;
    }

    fn jump_to(&mut self, line: Option<usize>) {
        let Some(line) = line else {
            self.status = Some("No error in this log".to_owned());
            return;
        };
        self.tasks.follow = false;
        self.tasks.cursor = Some(line);
        self.tasks.scroll = line.saturating_sub(3);
    }

    fn open_test(&mut self, test: &TestCase) {
        match test.failure_frame() {
            Some((file, line)) => self.open_location(PathBuf::from(file), line, Some(test)),
            None => {
                let file = test.class.rsplit('.').next().map(|name| format!("{name}.kt"));
                match file {
                    Some(file) => self.open_location(PathBuf::from(file), 1, Some(test)),
                    None => self.status = Some("The test has no source lvm-tui can find".to_owned()),
                }
            }
        }
    }

    /// Opens [file] at [line]: a path as Gradle printed it, or a file name from a stack trace, which is looked up in
    /// the test's module or in every module.
    fn open_location(&mut self, file: PathBuf, line: usize, test: Option<&TestCase>) {
        let file = if file.is_absolute() && file.is_file() {
            Some(file)
        } else if self.project.root.join(&file).is_file() {
            Some(self.project.root.join(&file))
        } else {
            let name = file.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
            let class = test.map(|t| t.class.as_str()).unwrap_or_default();
            let modules: Vec<&Module> = match test.and_then(|t| self.project.module(&t.module)) {
                Some(module) => vec![module],
                None => self.project.modules.iter().collect(),
            };
            modules.iter().find_map(|module| results::find_source(&self.project.root.join(&module.dir), &name, class))
        };
        match file {
            Some(file) => self.editor = Some(EditorRequest { file, line: Some(line) }),
            None => self.status = Some("lvm-tui can't find that file in the build".to_owned()),
        }
    }

    /// Reads the test results and detekt reports again, after a build.
    pub fn reload_results(&mut self) {
        self.results = Results::load(&self.project);
        let keep = |state: &mut ListState, count: usize| {
            state.select(if count == 0 { None } else { Some(state.selected().unwrap_or(0).min(count - 1)) });
        };
        keep(&mut self.tasks.tests, self.results.tests.len());
        keep(&mut self.tasks.issues, self.results.issues.len());
    }

    pub fn failed_tests(&self) -> usize {
        self.results.tests.iter().filter(|t| t.status == Status::Failed).count()
    }
}

/// Moves the selection of [state] for the arrow and j/k keys; returns whether [code] was one of them.
fn move_in(state: &mut ListState, count: usize, code: KeyCode) -> bool {
    let delta: isize = match code {
        KeyCode::Down | KeyCode::Char('j') => 1,
        KeyCode::Up | KeyCode::Char('k') => -1,
        KeyCode::PageDown => 10,
        KeyCode::PageUp => -10,
        KeyCode::Home | KeyCode::Char('g') => isize::MIN / 2,
        KeyCode::End | KeyCode::Char('G') => isize::MAX / 2,
        _ => return false,
    };
    if count > 0 {
        let current = state.selected().unwrap_or(0) as isize;
        state.select(Some(current.saturating_add(delta).clamp(0, count as isize - 1) as usize));
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test(module: &str, task: &str, class: &str, name: &str) -> TestCase {
        TestCase {
            module: module.into(),
            task: task.into(),
            class: class.into(),
            name: name.into(),
            seconds: 0.0,
            status: Status::Failed,
            message: None,
            trace: None,
        }
    }

    #[test]
    fn splits_command_lines_like_a_shell() {
        assert_eq!(split_args(":app:test --tests \"a b\"  -Px='y z'"), [":app:test", "--tests", "a b", "-Px=y z"]);
        assert_eq!(split_args("  "), Vec::<String>::new());
        assert_eq!(split_args("\"\""), [""]);
    }

    #[test]
    fn reruns_each_test_task_with_its_filters() {
        let tests = [
            test(":shared", "jvmTest", "com.example.A", "one"),
            test(":shared", "jvmTest", "com.example.A", "two words"),
            test(":app", "testDebugUnitTest", "B", "three"),
        ];
        assert_eq!(
            rerun_args(tests.iter()),
            [
                ":app:testDebugUnitTest",
                "--tests",
                "B.three",
                ":shared:jvmTest",
                "--tests",
                "com.example.A.one",
                "--tests",
                "com.example.A.two words",
            ]
        );
    }
}
