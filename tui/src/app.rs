//! The app's state and how it changes: every [Message] goes through [App::update], and the UI only draws the state.

use std::collections::VecDeque;
use std::path::PathBuf;
use std::time::Duration;

use ratatui::crossterm::event::{Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers};
use ratatui::widgets::{ListState, TableState};
use serde_json::Value;

use crate::doctor::{self, Environment, Finding, Fix};
use crate::edit::ChangeSet;
use crate::event::Message;
use crate::form::{Form, FormEvent};
use crate::gradle::{GradleEvent, JobId, Outcome, Purpose, Runner};
use crate::library_tab::LibraryView;
use crate::migrate;
use crate::project::Project;
use crate::project::model::{Module, ValueRow};
use crate::project::report::Source;
use crate::project::values_edit::{self, Layer};
use crate::results::Results;
use crate::run_tab::RunView;
use crate::state::State;
use crate::tasks::TasksView;
use crate::updates::UpdatesView;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Tab {
    Overview,
    Modules,
    Tasks,
    Run,
    Doctor,
    Updates,
    Library,
}

impl Tab {
    pub const ALL: [Tab; 7] =
        [Tab::Overview, Tab::Modules, Tab::Tasks, Tab::Run, Tab::Doctor, Tab::Updates, Tab::Library];

    pub fn title(self) -> &'static str {
        match self {
            Self::Overview => "Overview",
            Self::Modules => "Modules",
            Self::Tasks => "Tasks",
            Self::Run => "Run",
            Self::Doctor => "Doctor",
            Self::Updates => "Updates",
            Self::Library => "Library",
        }
    }

    fn id(self) -> &'static str {
        match self {
            Self::Overview => "overview",
            Self::Modules => "modules",
            Self::Tasks => "tasks",
            Self::Run => "run",
            Self::Doctor => "doctor",
            Self::Updates => "updates",
            Self::Library => "library",
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
    ShowTasks,
    ShowRun,
    ShowDoctor,
    ShowUpdates,
    ShowLibrary,
    UpgradeLvm,
    RunCommand,
    RerunFailedTests,
    EditValue,
    NewModule,
    AddDependency,
    MigrateModule,
    FixFinding,
    FixAll,
    OpenFile,
    ToggleAffected,
    FilterValues,
    Help,
    Quit,
}

impl Action {
    pub const ALL: [Action; 24] = [
        Action::RunReport,
        Action::Reload,
        Action::Cancel,
        Action::ShowOverview,
        Action::ShowModules,
        Action::ShowTasks,
        Action::ShowRun,
        Action::ShowDoctor,
        Action::ShowUpdates,
        Action::ShowLibrary,
        Action::UpgradeLvm,
        Action::RunCommand,
        Action::RerunFailedTests,
        Action::EditValue,
        Action::NewModule,
        Action::AddDependency,
        Action::MigrateModule,
        Action::FixFinding,
        Action::FixAll,
        Action::OpenFile,
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
            Self::ShowTasks => "Show the tasks, the log and the test results",
            Self::ShowRun => "Run an app on a device and follow its log",
            Self::ShowDoctor => "Show the doctor's findings",
            Self::ShowUpdates => "Show LVM's releases",
            Self::ShowLibrary => "Show the published modules: POM, signing, ABI",
            Self::UpgradeLvm => "Upgrade LVM to the selected release",
            Self::RunCommand => "Run a Gradle command",
            Self::RerunFailedTests => "Run the failed tests again",
            Self::EditValue => "Edit the selected value",
            Self::NewModule => "Create a module",
            Self::AddDependency => "Add an LVM library to the module",
            Self::MigrateModule => "Move the module's sources to the flat layout",
            Self::FixFinding => "Fix the selected finding",
            Self::FixAll => "Fix every finding that has a fix",
            Self::OpenFile => "Open the file in $EDITOR",
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
            Self::ShowTasks => "3",
            Self::ShowRun => "4",
            Self::ShowDoctor => "5",
            Self::ShowUpdates => "6",
            Self::ShowLibrary => "7",
            Self::UpgradeLvm => "u",
            Self::RunCommand => "c",
            Self::RerunFailedTests => "a",
            Self::EditValue => "e",
            Self::NewModule => "n",
            Self::AddDependency => "d",
            Self::MigrateModule => "m",
            Self::FixFinding => "f",
            Self::FixAll => "F",
            Self::OpenFile => "o",
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

/// A window over the tabs that takes the keys until it closes.
pub enum Dialog {
    /// A change to confirm, shown as a diff.
    Confirm { title: String, changes: ChangeSet, preview: Vec<String>, scroll: usize, then: AfterApply },
    /// A value of a module to set in one of its layers.
    Edit(EditDialog),
    /// Whether to run lvmReport and build after a change to the build.
    OfferBuild,
    /// A form that makes a change.
    Form { form: Form, purpose: FormPurpose },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FormPurpose {
    NewModule,
    /// Adds a library to the module at this path.
    AddDependency(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AfterApply {
    Nothing,
    OfferBuild,
}

pub struct EditDialog {
    pub key: String,
    pub module: String,
    pub value: Option<Value>,
    pub source: Source,
    pub layers: Vec<Layer>,
    pub layer: usize,
    pub input: String,
    pub error: Option<String>,
}

/// A build that runs or ran last.
pub struct JobView {
    pub id: JobId,
    pub purpose: Purpose,
    pub command: String,
    pub lines: VecDeque<String>,
    pub outcome: Option<(Outcome, Duration)>,
}

/// A file to open in the user's editor, which the main loop does with the terminal handed over.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EditorRequest {
    pub file: PathBuf,
    pub line: Option<usize>,
}

/// Lines kept of each build's output, and builds kept in the history.
const KEPT_LINES: usize = 20_000;
const KEPT_RUNS: usize = 20;

pub struct App {
    pub project: Project,
    pub tab: Tab,
    pub modules: ModulesView,
    pub tasks: TasksView,
    /// Test results and detekt issues of the last builds.
    pub results: Results,
    pub doctor: ListState,
    /// What the machine offers; the doctor waits for it, since probing takes a moment.
    pub env: Option<Environment>,
    pub findings: Vec<Finding>,
    pub dialog: Option<Dialog>,
    pub palette: Option<Palette>,
    pub help: bool,
    pub status: Option<String>,
    pub updates: UpdatesView,
    pub library: LibraryView,
    pub run: RunView,
    /// Whether the Updates tab asks Maven Central; tests turn it off and send the answers themselves.
    pub fetch_releases: bool,
    /// The builds of this session, the latest last.
    pub runs: Vec<JobView>,
    pub editor: Option<EditorRequest>,
    pub ticks: usize,
    pub quit: bool,
    pub(crate) runner: Runner<Message>,
}

impl App {
    pub fn new(project: Project, runner: Runner<Message>, state: &State) -> Self {
        let restored = Tab::ALL.into_iter().find(|tab| state.tab.as_deref() == Some(tab.id()));
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
        let mut app = Self {
            project,
            tab: Tab::Overview,
            modules,
            tasks: TasksView::default(),
            results: Results::default(),
            doctor: ListState::default(),
            env: None,
            findings: Vec::new(),
            dialog: None,
            palette: None,
            help: false,
            status: None,
            updates: UpdatesView::default(),
            library: LibraryView::default(),
            run: RunView { remembered: state.device.clone(), ..RunView::default() },
            fetch_releases: true,
            runs: Vec::new(),
            editor: None,
            ticks: 0,
            quit: false,
            runner,
        };
        app.reload_results();
        app.refresh_runnables();
        // Opens the tab as a key would, which keeps Overview where the build no longer has the tab.
        if let Some(tab) = restored {
            app.show_tab(tab);
        }
        app
    }

    /// The tabs of this build: Library only where a module publishes.
    pub fn tabs(&self) -> Vec<Tab> {
        let publishes = !self.published_modules().is_empty();
        Tab::ALL.into_iter().filter(|tab| *tab != Tab::Library || publishes).collect()
    }

    pub fn show_tab(&mut self, tab: Tab) {
        if !self.tabs().contains(&tab) {
            return;
        }
        self.tab = tab;
        match tab {
            Tab::Updates => self.open_updates(),
            Tab::Library => self.refresh_library(),
            Tab::Run => self.open_run(),
            _ => {}
        }
    }

    pub fn state(&self) -> State {
        State {
            tab: Some(self.tab.id().to_owned()),
            module: self.selected_module().map(|m| m.path.clone()),
            device: self.run.remembered.clone(),
        }
    }

    pub fn selected_module(&self) -> Option<&Module> {
        self.modules.list.selected().and_then(|i| self.project.modules.get(i))
    }

    pub fn selected_finding(&self) -> Option<&Finding> {
        self.doctor.selected().and_then(|i| self.findings.get(i))
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
            Message::Environment(env) => {
                self.env = Some(env);
                self.diagnose();
            }
            Message::Releases(versions) => self.on_releases(versions),
            Message::Catalog { version, catalog } => self.on_catalog(version, catalog),
            Message::Devices { tools, devices } => self.on_devices(tools, devices),
            Message::AppLog { stream, line } => self.on_app_log(stream, line),
            Message::AppLogEnded { stream, reason } => self.on_app_log_ended(stream, reason),
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
            Action::ShowOverview => self.show_tab(Tab::Overview),
            Action::ShowModules => self.show_tab(Tab::Modules),
            Action::ShowTasks => self.show_tab(Tab::Tasks),
            Action::ShowRun => self.show_tab(Tab::Run),
            Action::ShowDoctor => self.show_tab(Tab::Doctor),
            Action::ShowUpdates => self.show_tab(Tab::Updates),
            Action::ShowLibrary => self.show_tab(Tab::Library),
            Action::UpgradeLvm => {
                self.tab = Tab::Updates;
                self.open_updates();
                self.upgrade_selected();
            }
            Action::RunCommand => {
                self.tab = Tab::Tasks;
                let prefix = self.selected_target().map(|m| format!("{}:", m.path)).filter(|p| p != "::");
                self.tasks.command = Some(prefix.unwrap_or_default());
            }
            Action::RerunFailedTests => {
                self.tab = Tab::Tasks;
                let args = crate::tasks::rerun_args(self.results.failed());
                if args.is_empty() {
                    self.status = Some("No test failed".to_owned());
                } else {
                    self.run_gradle(args);
                }
            }
            Action::EditValue => self.edit_value(),
            Action::NewModule => {
                self.tab = Tab::Modules;
                let mut form = crate::wizard::module_form(&self.project);
                crate::wizard::sync_module_form(&mut form);
                self.dialog = Some(Dialog::Form { form, purpose: FormPurpose::NewModule });
            }
            Action::AddDependency => match self.selected_module() {
                Some(module) if module.is_lvm() => {
                    let form = crate::wizard::dependency_form(module);
                    let purpose = FormPurpose::AddDependency(module.path.clone());
                    self.tab = Tab::Modules;
                    self.dialog = Some(Dialog::Form { form, purpose });
                }
                _ => self.status = Some("Select a module with an LVM plugin in the Modules tab first".to_owned()),
            },
            Action::MigrateModule => self.migrate_module(),
            Action::FixFinding => self.fix_selected(),
            Action::FixAll => self.fix_all(),
            Action::OpenFile => self.open_file(),
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
        if self.dialog.is_some() {
            self.on_dialog_key(key);
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
        // Text typed into the Tasks tab goes there, not to the keys of the app.
        if self.tab == Tab::Tasks && (self.tasks.command.is_some() || self.tasks.filtering) {
            self.on_tasks_key(key);
            return;
        }
        if self.tab == Tab::Run && (self.run.file_input.is_some() || self.run.searching) {
            self.on_run_key(key);
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
            KeyCode::Char(c @ '1'..='9') => {
                let index = c as usize - '1' as usize;
                if let Some(tab) = self.tabs().get(index).copied() {
                    self.show_tab(tab);
                }
            }
            KeyCode::Tab | KeyCode::BackTab => {
                let tabs = self.tabs();
                let position = tabs.iter().position(|tab| *tab == self.tab).unwrap_or(0);
                let step = if key.code == KeyCode::Tab { 1 } else { tabs.len() - 1 };
                self.show_tab(tabs[(position + step) % tabs.len()]);
            }
            _ if self.tab == Tab::Modules => self.on_modules_key(key),
            _ if self.tab == Tab::Tasks => self.on_tasks_key(key),
            _ if self.tab == Tab::Run => self.on_run_key(key),
            _ if self.tab == Tab::Doctor => self.on_doctor_key(key),
            _ if self.tab == Tab::Updates => self.on_updates_key(key),
            _ if self.tab == Tab::Library => self.on_library_key(key),
            _ => {}
        }
    }

    fn on_modules_key(&mut self, key: KeyEvent) {
        match key.code {
            KeyCode::Char('/') => self.perform(Action::FilterValues),
            KeyCode::Char('a') => self.perform(Action::ToggleAffected),
            KeyCode::Char('e') => self.perform(Action::EditValue),
            KeyCode::Char('m') => self.perform(Action::MigrateModule),
            KeyCode::Char('n') => self.perform(Action::NewModule),
            KeyCode::Char('d') => self.perform(Action::AddDependency),
            KeyCode::Char('o') => self.perform(Action::OpenFile),
            KeyCode::Left | KeyCode::Char('h') => self.modules.focus = Focus::List,
            KeyCode::Right | KeyCode::Char('l') => self.modules.focus = Focus::Values,
            KeyCode::Enter if self.modules.focus == Focus::Values => self.perform(Action::EditValue),
            KeyCode::Enter => self.modules.focus = Focus::Values,
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

    fn on_doctor_key(&mut self, key: KeyEvent) {
        let count = self.findings.len();
        match key.code {
            KeyCode::Char('f') | KeyCode::Enter => self.perform(Action::FixFinding),
            KeyCode::Char('F') => self.perform(Action::FixAll),
            KeyCode::Char('o') => self.perform(Action::OpenFile),
            KeyCode::Down | KeyCode::Char('j') if count > 0 => {
                self.doctor.select(Some(self.doctor.selected().map_or(0, |i| (i + 1).min(count - 1))));
            }
            KeyCode::Up | KeyCode::Char('k') if count > 0 => {
                self.doctor.select(Some(self.doctor.selected().map_or(0, |i| i.saturating_sub(1))));
            }
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

    fn on_dialog_key(&mut self, key: KeyEvent) {
        let ctrl = key.modifiers.contains(KeyModifiers::CONTROL);
        match self.dialog.as_mut() {
            Some(Dialog::Confirm { scroll, preview, .. }) => match key.code {
                KeyCode::Char('y') | KeyCode::Enter => self.apply_dialog(),
                KeyCode::Char('n') | KeyCode::Esc | KeyCode::Char('q') => self.dialog = None,
                KeyCode::Down | KeyCode::Char('j') => *scroll = (*scroll + 1).min(preview.len().saturating_sub(1)),
                KeyCode::Up | KeyCode::Char('k') => *scroll = scroll.saturating_sub(1),
                KeyCode::PageDown | KeyCode::Char(' ') => *scroll = (*scroll + 20).min(preview.len().saturating_sub(1)),
                KeyCode::PageUp => *scroll = scroll.saturating_sub(20),
                _ => {}
            },
            Some(Dialog::Edit(edit)) => match key.code {
                KeyCode::Esc => self.dialog = None,
                KeyCode::Tab | KeyCode::BackTab => {
                    edit.layer = (edit.layer + 1) % edit.layers.len();
                    edit.error = None;
                }
                KeyCode::Char('d') if ctrl => self.confirm_edit(true),
                KeyCode::Enter => self.confirm_edit(false),
                KeyCode::Backspace => {
                    edit.input.pop();
                    edit.error = None;
                }
                KeyCode::Char(c) if !ctrl => {
                    edit.input.push(c);
                    edit.error = None;
                }
                _ => {}
            },
            Some(Dialog::Form { .. }) => self.on_form_key(key),
            Some(Dialog::OfferBuild) => match key.code {
                KeyCode::Char('y') | KeyCode::Enter => {
                    self.dialog = None;
                    self.run_report();
                    self.run_gradle(vec!["build".to_owned()]);
                }
                KeyCode::Char('n') | KeyCode::Esc | KeyCode::Char('q') => self.dialog = None,
                _ => {}
            },
            None => {}
        }
    }

    /// The values of the selected module that the filter keeps.
    pub fn visible_values(&self) -> Vec<&ValueRow> {
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

    fn edit_value(&mut self) {
        let (Some(module), Some(row)) = (
            self.selected_module(),
            self.modules.values.selected().and_then(|i| self.visible_values().get(i).copied()),
        ) else {
            self.status = Some("Select a value of a module first: 2, then l and j".to_owned());
            return;
        };
        let (path, key) = (module.path.clone(), row.key.clone());
        self.open_edit(&path, &key);
    }

    /// Opens the editor for the value [key] of the module at [path].
    pub(crate) fn open_edit(&mut self, path: &str, key: &str) {
        let Some(row) = self.project.module(path).and_then(|m| m.values.iter().find(|r| r.key == key)).cloned() else {
            self.status = Some(format!("{path} has no value {key}"));
            return;
        };
        let layers = values_edit::layers(&row.key);
        if layers.is_empty() {
            self.status = Some(format!("{} is not a value lvm-tui can edit", row.key));
            return;
        }
        // A module's own value is edited where it is; any other one most often belongs to the module too.
        let layer = match row.source {
            Source::Property => Layer::Properties,
            Source::Settings => Layer::Settings,
            Source::Module | Source::Default => Layer::Module,
        };
        let edit = EditDialog {
            key: row.key.clone(),
            module: path.to_owned(),
            value: row.value.clone(),
            source: row.source,
            layer: layers.iter().position(|l| *l == layer).unwrap_or(0),
            layers,
            input: values_edit::input_text(row.value.as_ref()),
            error: None,
        };
        self.dialog = Some(Dialog::Edit(edit));
    }

    fn confirm_edit(&mut self, remove: bool) {
        let Some(Dialog::Edit(edit)) = &mut self.dialog else { return };
        let layer = edit.layers[edit.layer];
        let value = if remove {
            None
        } else {
            match values_edit::parse_input(&edit.key, &edit.input) {
                Ok(value) => Some(value),
                Err(error) => {
                    edit.error = Some(error);
                    return;
                }
            }
        };
        let key = edit.key.clone();
        let module = self.project.module(&edit.module).cloned();
        match values_edit::change(&self.project, module.as_ref(), &key, layer, value.as_ref()) {
            Ok(changes) if changes.is_empty() => {
                if let Some(Dialog::Edit(edit)) = &mut self.dialog {
                    edit.error = Some(format!("{} doesn't set {key}: nothing to change", layer.label()));
                }
            }
            Ok(changes) => {
                let verb = if remove { "Remove" } else { "Set" };
                self.confirm(format!("{verb} {key} in {}", layer.label()), changes, AfterApply::Nothing);
            }
            Err(error) => {
                if let Some(Dialog::Edit(edit)) = &mut self.dialog {
                    edit.error = Some(format!("{error:#}"));
                }
            }
        }
    }

    fn on_form_key(&mut self, key: KeyEvent) {
        let Some(Dialog::Form { mut form, purpose }) = self.dialog.take() else { return };
        match form.key(key) {
            FormEvent::Cancel => return,
            FormEvent::Changed if purpose == FormPurpose::NewModule => crate::wizard::sync_module_form(&mut form),
            FormEvent::Submit => {
                let result = match &purpose {
                    FormPurpose::NewModule => {
                        let spec = crate::wizard::module_spec(&form);
                        crate::scaffold::add_module(&self.project, &spec)
                            .map(|changes| (format!("Create {}", spec.path), changes, Vec::new()))
                    }
                    FormPurpose::AddDependency(path) => match self.project.module(path) {
                        Some(module) => crate::wizard::dependency_spec(&form, module).and_then(|spec| {
                            crate::scaffold::dependency::add(&self.project, module, &spec)
                                .map(|(changes, notes)| (format!("Add {} to {path}", spec.artifact), changes, notes))
                        }),
                        None => Err(anyhow::anyhow!("{path} is gone from the build")),
                    },
                };
                match result {
                    Ok((title, changes, notes)) => {
                        self.confirm_with_notes(title, changes, AfterApply::OfferBuild, &notes);
                        return;
                    }
                    Err(error) => form.error = Some(format!("{error:#}")),
                }
            }
            _ => {}
        }
        self.dialog = Some(Dialog::Form { form, purpose });
    }

    fn migrate_module(&mut self) {
        let Some(module) = self.selected_module().cloned() else { return };
        let mut changes = ChangeSet::default();
        match migrate::flat_layout(&self.project, &module, &mut changes) {
            Ok(()) => self.confirm(format!("Move {} to the flat layout", module.path), changes, AfterApply::OfferBuild),
            Err(error) => self.status = Some(format!("{error:#}")),
        }
    }

    fn fix_selected(&mut self) {
        let Some(finding) = self.selected_finding().cloned() else { return };
        let Some(fix) = &finding.fix else {
            self.status = Some(format!("[{}] has no automatic fix", finding.rule));
            return;
        };
        self.fix(vec![fix.clone()], format!("Fix: {}", fix.label()));
    }

    fn fix_all(&mut self) {
        let fixes: Vec<Fix> = self.findings.iter().filter_map(|f| f.fix.clone()).collect();
        if fixes.is_empty() {
            self.status = Some("No finding has an automatic fix".to_owned());
            return;
        }
        let title = format!("Fix {} finding(s)", fixes.len());
        self.fix(fixes, title);
    }

    fn fix(&mut self, fixes: Vec<Fix>, title: String) {
        let mut changes = ChangeSet::default();
        for fix in &fixes {
            if let Err(error) = fix.apply(&self.project, &mut changes) {
                self.status = Some(format!("{error:#}"));
                return;
            }
        }
        let moves_sources = fixes.iter().any(|fix| matches!(fix, Fix::MigrateFlatLayout { .. }));
        self.confirm(title, changes, if moves_sources { AfterApply::OfferBuild } else { AfterApply::Nothing });
    }

    pub(crate) fn confirm(&mut self, title: String, changes: ChangeSet, then: AfterApply) {
        self.confirm_with_notes(title, changes, then, &[]);
    }

    /// Shows [changes] as a diff, after [notes] on what else they do.
    fn confirm_with_notes(&mut self, title: String, changes: ChangeSet, then: AfterApply, notes: &[String]) {
        let mut preview: Vec<String> = notes.iter().map(|note| format!("note: {note}")).collect();
        if !preview.is_empty() {
            preview.push(String::new());
        }
        preview.extend(changes.preview().lines().map(str::to_owned));
        self.dialog = Some(Dialog::Confirm { title, changes, preview, scroll: 0, then });
    }

    fn apply_dialog(&mut self) {
        let Some(Dialog::Confirm { changes, then, .. }) = self.dialog.take() else { return };
        match changes.apply(&self.project.root) {
            Ok(()) => {
                let files = changes.files.values().filter(|c| c.before != c.after).count();
                self.status = Some(format!("Changed {files} file(s) and moved {} file(s)", changes.moves.len()));
                if let Some(env) = &mut self.env {
                    env.recheck_git(&self.project.root);
                }
                self.reload();
                if then == AfterApply::OfferBuild {
                    self.dialog = Some(Dialog::OfferBuild);
                }
            }
            Err(error) => self.status = Some(format!("Could not apply the change: {error:#}")),
        }
    }

    fn open_file(&mut self) {
        let request = match self.tab {
            Tab::Doctor => self.selected_finding().and_then(|f| {
                let file = f.file.clone().or_else(|| self.project.module(f.module.as_deref()?)?.build_file.clone())?;
                Some(EditorRequest { file: self.project.root.join(file), line: f.line })
            }),
            _ => {
                self.selected_module().and_then(|m| m.build_file.clone()).map(|file| EditorRequest { file, line: None })
            }
        };
        match request {
            Some(request) => self.editor = Some(request),
            None => self.status = Some("No file to open".to_owned()),
        }
    }

    fn run_report(&mut self) {
        if self.runs.iter().any(|run| run.purpose == Purpose::Report && run.outcome.is_none()) {
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
                self.push_run(JobView { id: job, purpose, command, lines: VecDeque::new(), outcome: None });
            }
            GradleEvent::Line { job, line } => {
                self.on_run_output(job, &line);
                if let Some(run) = self.runs.iter_mut().rev().find(|run| run.id == job) {
                    if run.lines.len() == KEPT_LINES {
                        run.lines.pop_front();
                    }
                    run.lines.push_back(line);
                }
            }
            GradleEvent::Finished { job, outcome, elapsed } => {
                let finished = self.runner.finished(job);
                let purpose = finished.as_ref().map_or(Purpose::Task, |job| job.purpose);
                match self.runs.iter_mut().rev().find(|run| run.id == job) {
                    Some(run) => run.outcome = Some((outcome.clone(), elapsed)),
                    // A build that could not start has no Started event.
                    None => {
                        let command = finished.map(|job| job.args.join(" ")).unwrap_or_default();
                        let lines = VecDeque::new();
                        self.push_run(JobView {
                            id: job,
                            purpose,
                            command,
                            lines,
                            outcome: Some((outcome.clone(), elapsed)),
                        });
                    }
                }
                match purpose {
                    Purpose::Report => self.reload(),
                    Purpose::Task => self.reload_results(),
                }
                self.on_library_job(job, &outcome);
                self.on_run_finished(job, &outcome);
                self.status = Some(self.describe_outcome(job, &outcome, elapsed));
            }
        }
    }

    fn push_run(&mut self, run: JobView) {
        if self.runs.len() == KEPT_RUNS {
            self.runs.remove(0);
            self.tasks.run = self.tasks.run.and_then(|index| index.checked_sub(1));
        }
        self.runs.push(run);
    }

    fn describe_outcome(&self, job: JobId, outcome: &Outcome, elapsed: Duration) -> String {
        let run = self.runs.iter().rev().find(|run| run.id == job);
        let command = run.map_or_else(|| "Gradle".to_owned(), |run| run.command.clone());
        let seconds = elapsed.as_secs();
        match outcome {
            Outcome::Succeeded => format!("{command}: done in {seconds}s"),
            Outcome::Cancelled => format!("{command}: cancelled"),
            Outcome::NotStarted(reason) => format!("Could not start Gradle: {reason}"),
            Outcome::Failed(code) => {
                let reason = run.and_then(|run| failure_reason(&run.lines)).unwrap_or_default();
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
        self.diagnose();
        self.reload_results();
        self.refresh_runnables();
    }

    fn diagnose(&mut self) {
        let Some(env) = &self.env else { return };
        self.findings = doctor::diagnose(&self.project, env);
        let selected = self.doctor.selected().map(|i| i.min(self.findings.len().saturating_sub(1)));
        self.doctor.select(if self.findings.is_empty() { None } else { selected.or(Some(0)) });
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
