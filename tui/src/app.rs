//! The app's state and how it changes: every [Message] goes through [App::update], and the UI only draws the state.

use std::collections::VecDeque;
use std::time::Duration;

use ratatui::crossterm::event::{Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers};
use ratatui::widgets::{ListState, TableState};

use crate::event::Message;
use crate::gradle::{GradleEvent, JobId, Outcome, Purpose, Runner};
use crate::project::Project;
use crate::state::State;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Overview,
    Modules,
}

impl Tab {
    pub const ALL: [Tab; 2] = [Tab::Overview, Tab::Modules];

    pub fn title(self) -> &'static str {
        match self {
            Self::Overview => "Overview",
            Self::Modules => "Modules",
        }
    }

    fn id(self) -> &'static str {
        match self {
            Self::Overview => "overview",
            Self::Modules => "modules",
        }
    }
}

/// Something the user can do, from a key or the command palette.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    RunReport,
    Reload,
    Cancel,
    ShowOverview,
    ShowModules,
    ToggleAffected,
    FilterValues,
    Help,
    Quit,
}

impl Action {
    pub const ALL: [Action; 9] = [
        Action::RunReport,
        Action::Reload,
        Action::Cancel,
        Action::ShowOverview,
        Action::ShowModules,
        Action::ToggleAffected,
        Action::FilterValues,
        Action::Help,
        Action::Quit,
    ];

    pub fn label(self) -> &'static str {
        match self {
            Self::RunReport => "Run lvmReport and read the reports",
            Self::Reload => "Read the build files again",
            Self::Cancel => "Cancel the running build",
            Self::ShowOverview => "Show the overview",
            Self::ShowModules => "Show the modules",
            Self::ToggleAffected => "Show dependencies or affected modules",
            Self::FilterValues => "Filter the values of the module",
            Self::Help => "Show the keys",
            Self::Quit => "Quit",
        }
    }

    pub fn key(self) -> &'static str {
        match self {
            Self::RunReport => "r",
            Self::Reload => "R",
            Self::Cancel => "ctrl-c",
            Self::ShowOverview => "1",
            Self::ShowModules => "2",
            Self::ToggleAffected => "a",
            Self::FilterValues => "/",
            Self::Help => "?",
            Self::Quit => "q",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Focus {
    List,
    Values,
}

/// The lower pane of the Modules tab.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GraphPane {
    /// What the module depends on and what depends on it directly.
    Dependencies,
    /// Every module that a change to the module can affect.
    Affected,
}

pub struct ModulesView {
    pub list: ListState,
    pub values: TableState,
    pub focus: Focus,
    pub filter: String,
    pub filtering: bool,
    pub pane: GraphPane,
}

pub struct Palette {
    pub query: String,
    pub selected: usize,
}

impl Palette {
    pub fn matches(&self) -> Vec<Action> {
        Action::ALL.into_iter().filter(|action| fuzzy_match(&self.query, action.label())).collect()
    }
}

/// A build that runs or ran last.
pub struct JobView {
    pub id: JobId,
    pub purpose: Purpose,
    pub command: String,
    pub lines: VecDeque<String>,
    pub outcome: Option<(Outcome, Duration)>,
}

const KEPT_LINES: usize = 500;

pub struct App {
    pub project: Project,
    pub tab: Tab,
    pub modules: ModulesView,
    pub palette: Option<Palette>,
    pub help: bool,
    pub status: Option<String>,
    pub job: Option<JobView>,
    pub ticks: usize,
    pub quit: bool,
    runner: Runner<Message>,
}

impl App {
    pub fn new(project: Project, runner: Runner<Message>, state: &State) -> Self {
        let tab = Tab::ALL.into_iter().find(|tab| state.tab.as_deref() == Some(tab.id())).unwrap_or(Tab::Overview);
        let selected = state
            .module
            .as_deref()
            .and_then(|path| project.modules.iter().position(|m| m.path == path))
            .or((!project.modules.is_empty()).then_some(0));
        let modules = ModulesView {
            list: ListState::default().with_selected(selected),
            values: TableState::default(),
            focus: Focus::List,
            filter: String::new(),
            filtering: false,
            pane: GraphPane::Dependencies,
        };
        Self {
            project,
            tab,
            modules,
            palette: None,
            help: false,
            status: None,
            job: None,
            ticks: 0,
            quit: false,
            runner,
        }
    }

    pub fn state(&self) -> State {
        State { tab: Some(self.tab.id().to_owned()), module: self.selected_module().map(|m| m.path.clone()) }
    }

    pub fn selected_module(&self) -> Option<&crate::project::model::Module> {
        self.modules.list.selected().and_then(|i| self.project.modules.get(i))
    }

    pub fn is_running(&self) -> bool {
        self.runner.running().is_some()
    }

    pub fn update(&mut self, message: Message) {
        match message {
            Message::Tick => self.ticks = self.ticks.wrapping_add(1),
            Message::Input(Event::Key(key)) if key.kind != KeyEventKind::Release => self.on_key(key),
            Message::Input(_) => {}
            Message::Gradle(event) => self.on_gradle(event),
        }
    }

    pub fn perform(&mut self, action: Action) {
        match action {
            Action::RunReport => self.run_report(),
            Action::Reload => {
                self.reload();
                self.status = Some("Read the build files again".to_owned());
            }
            Action::Cancel => {
                if self.is_running() {
                    self.runner.cancel();
                    self.status = Some("Cancelling...".to_owned());
                }
            }
            Action::ShowOverview => self.tab = Tab::Overview,
            Action::ShowModules => self.tab = Tab::Modules,
            Action::ToggleAffected => {
                self.tab = Tab::Modules;
                self.modules.pane = match self.modules.pane {
                    GraphPane::Dependencies => GraphPane::Affected,
                    GraphPane::Affected => GraphPane::Dependencies,
                };
            }
            Action::FilterValues => {
                self.tab = Tab::Modules;
                self.modules.filtering = true;
                self.modules.focus = Focus::Values;
            }
            Action::Help => self.help = !self.help,
            Action::Quit => self.quit = true,
        }
    }

    fn on_key(&mut self, key: KeyEvent) {
        let ctrl = key.modifiers.contains(KeyModifiers::CONTROL);
        if ctrl && key.code == KeyCode::Char('c') {
            if self.is_running() {
                self.perform(Action::Cancel)
            } else {
                self.quit = true
            }
            return;
        }
        if self.palette.is_some() {
            self.on_palette_key(key);
            return;
        }
        if self.modules.filtering {
            self.on_filter_key(key);
            return;
        }
        if self.help {
            self.help = false;
            return;
        }
        if ctrl && key.code == KeyCode::Char('p') {
            self.palette = Some(Palette { query: String::new(), selected: 0 });
            return;
        }
        match key.code {
            KeyCode::Char('q') => self.perform(Action::Quit),
            KeyCode::Char('?') => self.perform(Action::Help),
            KeyCode::Char(':') => self.palette = Some(Palette { query: String::new(), selected: 0 }),
            KeyCode::Char('r') => self.perform(Action::RunReport),
            KeyCode::Char('R') => self.perform(Action::Reload),
            KeyCode::Char('1') => self.perform(Action::ShowOverview),
            KeyCode::Char('2') => self.perform(Action::ShowModules),
            KeyCode::Tab | KeyCode::BackTab => {
                self.tab = if self.tab == Tab::Overview { Tab::Modules } else { Tab::Overview };
            }
            _ if self.tab == Tab::Modules => self.on_modules_key(key),
            _ => {}
        }
    }

    fn on_modules_key(&mut self, key: KeyEvent) {
        match key.code {
            KeyCode::Char('/') => self.perform(Action::FilterValues),
            KeyCode::Char('a') => self.perform(Action::ToggleAffected),
            KeyCode::Left | KeyCode::Char('h') => self.modules.focus = Focus::List,
            KeyCode::Right | KeyCode::Char('l') | KeyCode::Enter => self.modules.focus = Focus::Values,
            KeyCode::Esc if !self.modules.filter.is_empty() => self.modules.filter.clear(),
            KeyCode::Down | KeyCode::Char('j') => self.move_selection(1),
            KeyCode::Up | KeyCode::Char('k') => self.move_selection(-1),
            KeyCode::PageDown => self.move_selection(10),
            KeyCode::PageUp => self.move_selection(-10),
            KeyCode::Home | KeyCode::Char('g') => self.move_selection(isize::MIN / 2),
            KeyCode::End | KeyCode::Char('G') => self.move_selection(isize::MAX / 2),
            _ => {}
        }
    }

    fn move_selection(&mut self, delta: isize) {
        let (state_len, selected) = match self.modules.focus {
            Focus::List => (self.project.modules.len(), self.modules.list.selected()),
            Focus::Values => (self.visible_values().len(), self.modules.values.selected()),
        };
        if state_len == 0 {
            return;
        }
        let current = selected.unwrap_or(0) as isize;
        let next = current.saturating_add(delta).clamp(0, state_len as isize - 1) as usize;
        match self.modules.focus {
            Focus::List => {
                self.modules.list.select(Some(next));
                self.modules.values = TableState::default();
            }
            Focus::Values => self.modules.values.select(Some(next)),
        }
    }

    fn on_filter_key(&mut self, key: KeyEvent) {
        match key.code {
            KeyCode::Enter => self.modules.filtering = false,
            KeyCode::Esc => {
                self.modules.filtering = false;
                self.modules.filter.clear();
            }
            KeyCode::Backspace => {
                self.modules.filter.pop();
            }
            KeyCode::Char(c) => self.modules.filter.push(c),
            _ => {}
        }
        self.modules.values.select(None);
    }

    fn on_palette_key(&mut self, key: KeyEvent) {
        let Some(palette) = &mut self.palette else { return };
        match key.code {
            KeyCode::Esc => self.palette = None,
            KeyCode::Enter => {
                let action = palette.matches().get(palette.selected).copied();
                self.palette = None;
                if let Some(action) = action {
                    self.perform(action);
                }
            }
            KeyCode::Down => palette.selected = (palette.selected + 1).min(palette.matches().len().saturating_sub(1)),
            KeyCode::Up => palette.selected = palette.selected.saturating_sub(1),
            KeyCode::Backspace => {
                palette.query.pop();
                palette.selected = 0;
            }
            KeyCode::Char(c) => {
                palette.query.push(c);
                palette.selected = 0;
            }
            _ => {}
        }
    }

    /// The values of the selected module that the filter keeps.
    pub fn visible_values(&self) -> Vec<&crate::project::model::ValueRow> {
        let filter = self.modules.filter.to_lowercase();
        self.selected_module()
            .map(|module| {
                module
                    .values
                    .iter()
                    .filter(|row| {
                        filter.is_empty()
                            || row.key.to_lowercase().contains(&filter)
                            || row.value.as_ref().is_some_and(|v| v.to_string().to_lowercase().contains(&filter))
                    })
                    .collect()
            })
            .unwrap_or_default()
    }

    fn run_report(&mut self) {
        if self.job.as_ref().is_some_and(|job| job.purpose == Purpose::Report && job.outcome.is_none()) {
            self.status = Some("lvmReport is already running".to_owned());
            return;
        }
        // --continue writes the reports of the other modules when one module fails to configure.
        self.runner.submit(vec!["lvmReport".to_owned(), "--continue".to_owned()], Purpose::Report);
        self.status = None;
    }

    fn on_gradle(&mut self, event: GradleEvent) {
        match event {
            GradleEvent::Started { job, command } => {
                let purpose =
                    self.runner.running().filter(|running| running.id == job).map_or(Purpose::Task, |r| r.purpose);
                self.job = Some(JobView { id: job, purpose, command, lines: VecDeque::new(), outcome: None });
            }
            GradleEvent::Line { job, line } => {
                if let Some(view) = self.job.as_mut().filter(|view| view.id == job) {
                    if view.lines.len() == KEPT_LINES {
                        view.lines.pop_front();
                    }
                    view.lines.push_back(line);
                }
            }
            GradleEvent::Finished { job, outcome, elapsed } => {
                let finished = self.runner.finished(job);
                let purpose = finished.as_ref().map_or(Purpose::Task, |job| job.purpose);
                match self.job.as_mut().filter(|view| view.id == job) {
                    Some(view) => view.outcome = Some((outcome.clone(), elapsed)),
                    None => {
                        let command = finished.map(|job| job.args.join(" ")).unwrap_or_default();
                        self.job = Some(JobView {
                            id: job,
                            purpose,
                            command,
                            lines: VecDeque::new(),
                            outcome: Some((outcome.clone(), elapsed)),
                        });
                    }
                }
                if purpose == Purpose::Report {
                    self.reload();
                }
                self.status = Some(self.describe_outcome(&outcome, elapsed));
            }
        }
    }

    fn describe_outcome(&self, outcome: &Outcome, elapsed: Duration) -> String {
        let command = self.job.as_ref().map(|job| job.command.as_str()).unwrap_or("Gradle");
        let seconds = elapsed.as_secs();
        match outcome {
            Outcome::Succeeded => format!("{command}: done in {seconds}s"),
            Outcome::Cancelled => format!("{command}: cancelled"),
            Outcome::NotStarted(reason) => format!("Could not start Gradle: {reason}"),
            Outcome::Failed(code) => {
                let reason = self.job.as_ref().and_then(|job| failure_reason(&job.lines)).unwrap_or_default();
                let code = code.map(|c| format!(" (exit {c})")).unwrap_or_default();
                format!("{command}: failed{code} {reason}").trim_end().to_owned()
            }
        }
    }

    /// Reads the build again and keeps the selected module where it still exists.
    pub fn reload(&mut self) {
        let selected = self.selected_module().map(|m| m.path.clone());
        self.project = Project::load(&self.project.root);
        let index = selected.and_then(|path| self.project.modules.iter().position(|m| m.path == path)).or((!self
            .project
            .modules
            .is_empty())
        .then_some(0));
        self.modules.list.select(index);
    }
}

/// The first line after Gradle's `* What went wrong:`.
fn failure_reason(lines: &VecDeque<String>) -> Option<String> {
    let position = lines.iter().position(|line| line.starts_with("* What went wrong:"))?;
    lines.iter().skip(position + 1).find(|line| !line.trim().is_empty()).map(|line| line.trim().to_owned())
}

/// Whether the letters of [query] appear in [text] in order, ignoring case.
pub fn fuzzy_match(query: &str, text: &str) -> bool {
    let mut letters = text.chars().flat_map(char::to_lowercase);
    query.chars().flat_map(char::to_lowercase).filter(|c| !c.is_whitespace()).all(|q| letters.any(|c| c == q))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fuzzy_matching_follows_the_order_of_letters() {
        assert!(fuzzy_match("rep", "Run lvmReport and read the reports"));
        assert!(fuzzy_match("q", "Quit"));
        assert!(fuzzy_match("", "anything"));
        assert!(!fuzzy_match("tq", "Quit"));
    }

    #[test]
    fn reads_the_reason_of_a_failed_build() {
        let lines: VecDeque<String> =
            ["> Task :a FAILED", "", "* What went wrong:", "Execution failed for task ':a'.", "> boom"]
                .map(String::from)
                .into();
        assert_eq!(failure_reason(&lines).as_deref(), Some("Execution failed for task ':a'."));
    }
}
