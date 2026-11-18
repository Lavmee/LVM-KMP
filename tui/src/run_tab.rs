//! The Run tab: an app of the build on a device, a simulator or the JVM, and its log.

use std::collections::VecDeque;
use std::path::PathBuf;
use std::thread;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::widgets::ListState;

use crate::app::App;
use crate::applog::{Level, LogLine, LogStream, Source};
use crate::devices::{self, Device, State, Tools};
use crate::event::Message;
use crate::gradle::{self, JobId};
use crate::run::{self, RunKind, RunTools, Runnable};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RunFocus {
    Targets,
    Devices,
    Log,
}

/// Lines kept of an app's log.
const KEPT_LOG: usize = 20_000;

pub struct RunView {
    pub focus: RunFocus,
    /// What the build can run, found when the tab opens and after the build is read again.
    pub runnables: Vec<Runnable>,
    pub targets: ListState,
    pub devices_list: ListState,
    pub tools: Tools,
    /// The devices, once adb, the emulator and simctl answered.
    pub devices: Option<Vec<Device>>,
    /// Whether the tab lists devices; tests turn it off and send them.
    pub probe: bool,
    /// The device to select once the list arrives, from the last run.
    pub remembered: Option<String>,
    pub log: VecDeque<LogLine>,
    /// Where the log comes from, and why it ended when it did.
    pub source: Option<String>,
    pub ended: Option<String>,
    pub min_level: Level,
    pub search: String,
    pub searching: bool,
    /// Holds the view where it is while lines keep arriving.
    pub paused: bool,
    pub scroll: usize,
    /// A path being typed after `F`.
    pub file_input: Option<String>,
    /// The JVM run whose output the log shows.
    pub jvm_job: Option<JobId>,
    pub(crate) stream: Option<LogStream>,
    /// The id of the latest stream; lines of the ones before it are late and dropped.
    pub(crate) stream_id: u64,
}

impl Default for RunView {
    fn default() -> Self {
        Self {
            focus: RunFocus::Targets,
            runnables: Vec::new(),
            targets: ListState::default().with_selected(Some(0)),
            devices_list: ListState::default().with_selected(Some(0)),
            tools: Tools::default(),
            devices: None,
            probe: true,
            remembered: None,
            log: VecDeque::new(),
            source: None,
            ended: None,
            min_level: Level::Verbose,
            search: String::new(),
            searching: false,
            paused: false,
            scroll: 0,
            file_input: None,
            jvm_job: None,
            stream: None,
            stream_id: 0,
        }
    }
}

impl RunView {
    /// The lines that the level and the search keep.
    pub fn visible(&self) -> Vec<&LogLine> {
        self.log.iter().filter(|line| line.level >= self.min_level && line.matches(&self.search)).collect()
    }

    fn push(&mut self, line: LogLine) {
        if self.log.len() == KEPT_LOG {
            self.log.pop_front();
        }
        self.log.push_back(line);
    }

    fn follow(&mut self, label: String, stream: Option<LogStream>) {
        self.stream = stream;
        self.log.clear();
        self.source = Some(label);
        self.ended = None;
        self.paused = false;
        self.focus = RunFocus::Log;
    }
}

impl App {
    pub fn selected_runnable(&self) -> Option<Runnable> {
        self.run.runnables.get(self.run.targets.selected().unwrap_or(0)).cloned()
    }

    /// Finds what the build can run again, keeping the selected one where it still exists.
    pub(crate) fn refresh_runnables(&mut self) {
        let selected = self.selected_runnable();
        self.run.runnables = run::runnables(&self.project);
        let index = selected.and_then(|old| self.run.runnables.iter().position(|r| *r == old));
        self.run.targets.select(Some(index.unwrap_or(0)));
    }

    /// The devices for the selected app's platform; none for the JVM, which runs here.
    pub fn platform_devices(&self) -> Vec<Device> {
        let Some(runnable) = self.selected_runnable() else { return Vec::new() };
        let platform = match runnable.kind {
            RunKind::IosFramework | RunKind::Jvm { .. } => return Vec::new(),
            _ => runnable.platform,
        };
        self.run.devices.iter().flatten().filter(|d| d.platform == platform).cloned().collect()
    }

    pub fn selected_device(&self) -> Option<Device> {
        self.platform_devices().into_iter().nth(self.run.devices_list.selected().unwrap_or(0))
    }

    /// Lists the devices the first time the tab opens.
    pub(crate) fn open_run(&mut self) {
        self.refresh_runnables();
        if self.run.devices.is_none() && self.run.probe {
            self.probe_devices();
        }
    }

    fn probe_devices(&mut self) {
        let sender = self.runner.sender();
        let root = self.project.root.clone();
        thread::spawn(move || {
            let tools = Tools::find(devices::android_sdk(&root).as_deref());
            let devices = devices::probe(&tools);
            let _ = sender.send(Message::Devices { tools, devices });
        });
    }

    pub(crate) fn on_devices(&mut self, tools: Tools, devices: Vec<Device>) {
        self.run.tools = tools;
        self.run.devices = Some(devices);
        self.select_remembered_device();
    }

    /// Selects the device of the last run where the selected app can use it, else the first one.
    fn select_remembered_device(&mut self) {
        let remembered = self.run.remembered.clone();
        let index = remembered.and_then(|id| self.platform_devices().iter().position(|d| d.id == id));
        self.run.devices_list.select(Some(index.unwrap_or(0)));
    }

    pub(crate) fn on_app_log(&mut self, stream: u64, line: LogLine) {
        if stream == self.run.stream_id && self.run.stream.is_some() {
            self.run.push(line);
        }
    }

    pub(crate) fn on_app_log_ended(&mut self, stream: u64, reason: String) {
        if stream == self.run.stream_id && self.run.stream.is_some() {
            self.run.stream = None;
            self.run.ended = Some(reason);
        }
    }

    /// Follows [source] in place of the log shown before.
    fn follow_source(&mut self, source: Source) {
        let label = match &source {
            // A file in the build is named from its root.
            Source::File(path) => match path.strip_prefix(&self.project.root) {
                Ok(relative) => format!("file {}", relative.display()),
                Err(_) => source.label(),
            },
            _ => source.label(),
        };
        self.run.stream_id += 1;
        let stream = LogStream::start(self.run.stream_id, source, self.runner.sender());
        self.run.jvm_job = None;
        self.run.follow(label, Some(stream));
    }

    /// The output of the JVM run that the log follows.
    pub(crate) fn on_run_output(&mut self, job: JobId, line: &str) {
        if self.run.jvm_job == Some(job) {
            self.run.push(crate::applog::parse_kermit(line));
        }
    }

    pub(crate) fn on_run_finished(&mut self, job: JobId, outcome: &gradle::Outcome) {
        if self.run.jvm_job == Some(job) {
            self.run.ended = Some(match outcome {
                gradle::Outcome::Succeeded => "The app exited".to_owned(),
                gradle::Outcome::Cancelled => "Cancelled".to_owned(),
                gradle::Outcome::Failed(code) => {
                    format!("Failed{}", code.map(|c| format!(" (exit {c})")).unwrap_or_default())
                }
                gradle::Outcome::NotStarted(reason) => format!("Could not start: {reason}"),
            });
        }
    }

    pub(crate) fn on_run_key(&mut self, key: KeyEvent) {
        if let Some(input) = self.run.file_input.as_mut() {
            match key.code {
                KeyCode::Esc => self.run.file_input = None,
                KeyCode::Enter => {
                    let path = input.trim().to_owned();
                    self.run.file_input = None;
                    self.follow_file(&path);
                }
                KeyCode::Backspace => {
                    input.pop();
                }
                KeyCode::Char(c) => input.push(c),
                _ => {}
            }
            return;
        }
        if self.run.searching {
            match key.code {
                KeyCode::Enter => self.run.searching = false,
                KeyCode::Esc => {
                    self.run.searching = false;
                    self.run.search.clear();
                }
                KeyCode::Backspace => {
                    self.run.search.pop();
                }
                KeyCode::Char(c) => self.run.search.push(c),
                _ => {}
            }
            return;
        }
        match key.code {
            KeyCode::Char('h') | KeyCode::Left => {
                self.run.focus = match self.run.focus {
                    RunFocus::Log => RunFocus::Devices,
                    _ => RunFocus::Targets,
                };
            }
            KeyCode::Char('l') | KeyCode::Right => {
                self.run.focus = match self.run.focus {
                    RunFocus::Targets => RunFocus::Devices,
                    _ => RunFocus::Log,
                };
            }
            KeyCode::Down | KeyCode::Char('j') => self.move_run(1),
            KeyCode::Up | KeyCode::Char('k') => self.move_run(-1),
            KeyCode::Enter => self.run_selected(),
            KeyCode::Char('s') => self.start_device(),
            KeyCode::Char('f') => {
                self.run.devices = None;
                self.probe_devices();
            }
            KeyCode::Char('a') => self.follow_app_log(),
            KeyCode::Char('F') => self.run.file_input = Some(String::new()),
            KeyCode::Char('L') => {
                let position = Level::ALL.iter().position(|l| *l == self.run.min_level).unwrap_or(0);
                self.run.min_level = Level::ALL[(position + 1) % Level::ALL.len()];
            }
            KeyCode::Char('/') => self.run.searching = true,
            KeyCode::Char(' ') => {
                self.run.paused = !self.run.paused;
                self.run.scroll = self.run.visible().len().saturating_sub(1);
            }
            KeyCode::Char('C') => self.run.log.clear(),
            KeyCode::Char('x') => {
                self.run.stream = None;
                self.run.ended = Some("Stopped".to_owned());
            }
            _ => {}
        }
    }

    fn move_run(&mut self, delta: isize) {
        let (state, count) = match self.run.focus {
            RunFocus::Targets => (&mut self.run.targets, self.run.runnables.len()),
            RunFocus::Devices => {
                let count = self.platform_devices().len();
                (&mut self.run.devices_list, count)
            }
            RunFocus::Log => {
                self.run.paused = true;
                let count = self.run.visible().len();
                self.run.scroll =
                    (self.run.scroll as isize + delta).clamp(0, count.saturating_sub(1) as isize) as usize;
                return;
            }
        };
        if count > 0 {
            let next = (state.selected().unwrap_or(0) as isize + delta).clamp(0, count as isize - 1);
            state.select(Some(next as usize));
        }
        if self.run.focus == RunFocus::Targets {
            self.select_remembered_device();
        }
    }

    fn run_selected(&mut self) {
        let Some(runnable) = self.selected_runnable() else {
            self.status = Some("Nothing to run: the build has no Android app, iOS framework or JVM app".to_owned());
            return;
        };
        if cfg!(windows) && !matches!(runnable.kind, RunKind::Jvm { .. } | RunKind::IosFramework) {
            self.status = Some("Running apps needs a Unix shell; on Windows, use the Tasks tab".to_owned());
            return;
        }
        let device = self.selected_device();
        if let Some(device) = &device {
            let problem = match &device.state {
                State::Ready => None,
                State::Stopped => Some(format!("{} isn't running: s starts it", device.name)),
                State::Unusable(reason) => Some(format!("{} is {reason}: adb can't install on it", device.name)),
            };
            if problem.is_some() {
                self.status = problem;
                return;
            }
        }
        let gradle = self.runner.gradle_path();
        let tools = RunTools { root: &self.project.root, gradle: &gradle, adb: self.run.tools.adb.as_deref() };
        match run::program(&runnable, device.as_ref(), &tools) {
            Ok(program) => {
                let job = self.runner.submit_program(program, gradle::Purpose::Task);
                self.status = None;
                if let Some(device) = device {
                    self.run.remembered = Some(device.id);
                }
                if let RunKind::Jvm { task } = &runnable.kind {
                    self.run.jvm_job = Some(job);
                    self.run.follow(format!("output of {}:{task}", runnable.module), None);
                }
            }
            Err(reason) => self.status = Some(reason),
        }
    }

    fn start_device(&mut self) {
        let Some(device) = self.selected_device() else { return };
        match run::start_program(&device, self.run.tools.emulator.as_deref()) {
            Ok(program) => {
                self.runner.submit_program(program, gradle::Purpose::Task);
                self.status = Some(format!("Starting {}: f lists the devices again once it is up", device.name));
            }
            Err(reason) => self.status = Some(reason),
        }
    }

    /// Follows the selected app's log on the selected device.
    fn follow_app_log(&mut self) {
        let Some(runnable) = self.selected_runnable() else { return };
        let device = self.selected_device();
        let source = match (&runnable.kind, device) {
            (RunKind::AndroidApp { application_id }, Some(device)) => match &self.run.tools.adb {
                Some(adb) => {
                    Source::Logcat { adb: adb.clone(), serial: device.id, application_id: application_id.clone() }
                }
                None => {
                    self.status = Some("No adb: set ANDROID_HOME to the Android SDK".to_owned());
                    return;
                }
            },
            (RunKind::IosApp { project }, Some(device)) => {
                let process = project.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
                Source::Simulator { udid: device.id, process }
            }
            (RunKind::Jvm { .. }, _) => {
                self.status = Some("The JVM app's output shows here when it runs from this tab".to_owned());
                return;
            }
            _ => {
                self.status = Some("Pick a running device first".to_owned());
                return;
            }
        };
        self.follow_source(source);
    }

    /// Follows a file of lvm-logger-file: on this computer, or in the Android app's data directory on the device.
    fn follow_file(&mut self, path: &str) {
        if path.is_empty() {
            return;
        }
        let local = PathBuf::from(path);
        let source = if local.is_absolute() || local.exists() {
            Source::File(local)
        } else {
            match (self.selected_runnable().map(|r| r.kind), self.selected_device(), &self.run.tools.adb) {
                (Some(RunKind::AndroidApp { application_id }), Some(device), Some(adb)) => {
                    Source::DeviceFile { adb: adb.clone(), serial: device.id, application_id, path: path.to_owned() }
                }
                _ => {
                    self.status =
                        Some(format!("{path} doesn't exist here; a relative path is read on an Android device"));
                    return;
                }
            }
        };
        self.follow_source(source);
    }
}
