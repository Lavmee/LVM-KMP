//! The Compose tab: the modules with Compose, the compiler's metrics and what its reports say to look at.

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::widgets::ListState;

use crate::app::{App, EditorRequest};
use crate::compose::{self, ComposeReport, Finding};
use crate::gradle::{JobId, Outcome};
use crate::project::model::Module;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ComposeFocus {
    Modules,
    Findings,
}

pub struct ComposeView {
    pub focus: ComposeFocus,
    pub list: ListState,
    pub findings: ListState,
    /// The reports of the selected module, read again after each build.
    pub report: ComposeReport,
    /// The build that writes the reports of a module, and its path.
    pending: Option<(JobId, String)>,
}

impl Default for ComposeView {
    fn default() -> Self {
        Self {
            focus: ComposeFocus::Modules,
            list: ListState::default().with_selected(Some(0)),
            findings: ListState::default(),
            report: ComposeReport::default(),
            pending: None,
        }
    }
}

impl App {
    /// The modules that apply `tech.annexflow.lvm.compose`.
    pub fn compose_modules(&self) -> Vec<&Module> {
        self.project.modules.iter().filter(|m| m.has_plugin("compose")).collect()
    }

    pub fn selected_compose_module(&self) -> Option<&Module> {
        self.compose_modules().get(self.compose.list.selected().unwrap_or(0)).copied()
    }

    pub fn compose_findings(&self) -> Vec<Finding> {
        compose::findings(&self.compose.report)
    }

    /// Reads the selected module's reports again.
    pub(crate) fn refresh_compose(&mut self) {
        let Some(module) = self.selected_compose_module() else {
            self.compose.report = ComposeReport::default();
            return;
        };
        let reported = self.project.reports.modules.get(&module.path).and_then(|r| r.reports.as_ref());
        let (reports, metrics) = compose::directories(module, reported);
        self.compose.report = compose::load(&self.project.root.join(reports), &self.project.root.join(metrics));
        let count = self.compose_findings().len();
        let selected = self.compose.findings.selected().map(|i| i.min(count.saturating_sub(1)));
        self.compose.findings.select(if count == 0 { None } else { selected.or(Some(0)) });
    }

    pub(crate) fn on_compose_key(&mut self, key: KeyEvent) {
        match key.code {
            KeyCode::Char('h') | KeyCode::Left => self.compose.focus = ComposeFocus::Modules,
            KeyCode::Char('l') | KeyCode::Right => self.compose.focus = ComposeFocus::Findings,
            KeyCode::Down | KeyCode::Char('j') => self.move_compose(1),
            KeyCode::Up | KeyCode::Char('k') => self.move_compose(-1),
            KeyCode::Char('c') => self.build_compose_reports(),
            KeyCode::Enter | KeyCode::Char('o') if self.compose.focus == ComposeFocus::Findings => self.open_finding(),
            KeyCode::Enter => self.compose.focus = ComposeFocus::Findings,
            _ => {}
        }
    }

    fn move_compose(&mut self, delta: isize) {
        let (state, count) = match self.compose.focus {
            ComposeFocus::Modules => {
                (&mut self.compose.list, self.project.modules.iter().filter(|m| m.has_plugin("compose")).count())
            }
            ComposeFocus::Findings => {
                let count = self.compose_findings().len();
                (&mut self.compose.findings, count)
            }
        };
        if count == 0 {
            return;
        }
        let next = (state.selected().map_or(-1, |i| i as isize) + delta).clamp(0, count as isize - 1);
        state.select(Some(next as usize));
        if self.compose.focus == ComposeFocus::Modules {
            self.compose.findings.select(None);
            self.refresh_compose();
        }
    }

    /// Compiles the selected module once with the reports and metrics on. `--rerun` compiles it again, since the
    /// reports' directories aren't inputs of the task.
    fn build_compose_reports(&mut self) {
        let Some(module) = self.selected_compose_module() else { return };
        let Some(task) = compose::compile_task(module) else {
            self.status = Some(format!("Found no compilation of {}: run r first", module.path));
            return;
        };
        let path = module.path.clone();
        let mut args = vec![task, "--rerun".to_owned()];
        args.extend(compose::PROPERTIES.map(str::to_owned));
        let job = self.run_gradle(args);
        self.compose.pending = Some((job, path));
    }

    fn open_finding(&mut self) {
        let Some(module) = self.selected_compose_module() else { return };
        let Some(finding) = self.compose.findings.selected().and_then(|i| self.compose_findings().get(i).cloned())
        else {
            return;
        };
        let dir = self.project.root.join(&module.dir);
        match compose::locate(&dir, finding.declaration()) {
            Some((file, line)) => self.editor = Some(EditorRequest { file, line: Some(line) }),
            None => self.status = Some(format!("Found no declaration of {} in {}", finding.declaration(), module.dir)),
        }
    }

    /// To be called when a build finishes: reads the reports that a build of this tab wrote.
    pub(crate) fn on_compose_job(&mut self, job: JobId, outcome: &Outcome) {
        let Some((pending, path)) = self.compose.pending.take() else { return };
        if pending != job {
            self.compose.pending = Some((pending, path));
            return;
        }
        self.refresh_compose();
        if *outcome == Outcome::Succeeded && self.compose.report.is_empty() {
            self.status = Some(format!(
                "{path} wrote no Compose reports: check that its lvm {{ compose {{ }} }} doesn't turn them off"
            ));
        }
    }
}
