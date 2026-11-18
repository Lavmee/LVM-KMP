//! The Library tab: the published modules, their POM, signing, ABI dump and a dry run to Maven local.

use std::path::PathBuf;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::widgets::{ListState, TableState};

use crate::app::App;
use crate::gradle::{JobId, Outcome};
use crate::library::{self, AbiState, Place};
use crate::project::model::Module;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LibraryFocus {
    Modules,
    Pom,
    Abi,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LibraryJob {
    Check,
    Update,
    Publish,
}

pub struct LibraryView {
    pub focus: LibraryFocus,
    pub list: ListState,
    pub pom: TableState,
    pub abi_scroll: usize,
    /// The ABI of the selected module, read again after each build.
    pub abi: AbiState,
    /// What `updateKotlinAbi` changed in `api/` that nobody committed yet.
    pub uncommitted: Vec<String>,
    /// Where each secret is set, read when the tab opens.
    pub secrets: Vec<(&'static str, Vec<Place>)>,
    /// The files the last dry run left in Maven local.
    pub published: Option<Vec<PathBuf>>,
    /// Where Gradle's user properties are; tests point it elsewhere.
    pub gradle_home: Option<PathBuf>,
    pending: Option<(JobId, String, LibraryJob)>,
}

impl Default for LibraryView {
    fn default() -> Self {
        Self {
            focus: LibraryFocus::Modules,
            list: ListState::default().with_selected(Some(0)),
            pom: TableState::default(),
            abi_scroll: 0,
            abi: AbiState::NotChecked,
            uncommitted: Vec::new(),
            secrets: Vec::new(),
            published: None,
            gradle_home: library::gradle_user_home(),
            pending: None,
        }
    }
}

impl App {
    /// The modules that apply `tech.annexflow.lvm.publish`.
    pub fn published_modules(&self) -> Vec<&Module> {
        self.project.modules.iter().filter(|m| m.has_plugin("publish")).collect()
    }

    pub fn selected_library(&self) -> Option<&Module> {
        self.published_modules().get(self.library.list.selected().unwrap_or(0)).copied()
    }

    /// Reads the selected module's ABI and the secrets again.
    pub(crate) fn refresh_library(&mut self) {
        self.library.secrets = library::secrets(&self.project.properties, self.library.gradle_home.as_deref());
        let Some(module) = self.selected_library() else { return };
        let dir = self.project.root.join(&module.dir);
        self.library.abi = library::abi_state(&dir);
        self.library.uncommitted = library::uncommitted_dump(&self.project.root, &dir);
        self.library.abi_scroll = 0;
    }

    pub(crate) fn on_library_key(&mut self, key: KeyEvent) {
        let count = self.published_modules().len();
        let Some(module) = self.selected_library().cloned() else { return };
        let task = |name: &str| if module.path == ":" { format!(":{name}") } else { format!("{}:{name}", module.path) };
        match key.code {
            KeyCode::Char('h') | KeyCode::Left => {
                self.library.focus = match self.library.focus {
                    LibraryFocus::Abi => LibraryFocus::Pom,
                    _ => LibraryFocus::Modules,
                };
            }
            KeyCode::Char('l') | KeyCode::Right => {
                self.library.focus = match self.library.focus {
                    LibraryFocus::Modules => LibraryFocus::Pom,
                    _ => LibraryFocus::Abi,
                };
            }
            KeyCode::Down | KeyCode::Char('j') => self.move_library(1, count),
            KeyCode::Up | KeyCode::Char('k') => self.move_library(-1, count),
            KeyCode::Char('c') => self.run_library(task("checkKotlinAbi"), &module, LibraryJob::Check),
            KeyCode::Char('u') => self.run_library(task("updateKotlinAbi"), &module, LibraryJob::Update),
            KeyCode::Char('p') => {
                // A dry run: signing off, so a release version works without a key.
                let args = vec![task("publishToMavenLocal"), "-Plvm.publish.signing=false".to_owned()];
                let job = self.run_gradle(args);
                self.library.pending = Some((job, module.path.clone(), LibraryJob::Publish));
                self.library.published = None;
            }
            KeyCode::Char('e') | KeyCode::Enter if self.library.focus == LibraryFocus::Pom => {
                let rows = library::pom(&module);
                if let Some((key, _, _)) = self.library.pom.selected().and_then(|i| rows.get(i)) {
                    let key = key.clone();
                    self.open_edit(&module.path, &key);
                }
            }
            _ => {}
        }
    }

    fn move_library(&mut self, delta: isize, count: usize) {
        match self.library.focus {
            LibraryFocus::Modules if count > 0 => {
                let next = (self.library.list.selected().unwrap_or(0) as isize + delta).clamp(0, count as isize - 1);
                self.library.list.select(Some(next as usize));
                self.library.published = None;
                self.refresh_library();
            }
            LibraryFocus::Pom => {
                let rows = self.selected_library().map_or(0, |m| library::pom(m).len());
                if rows > 0 {
                    let next =
                        (self.library.pom.selected().map_or(-1, |i| i as isize) + delta).clamp(0, rows as isize - 1);
                    self.library.pom.select(Some(next as usize));
                }
            }
            LibraryFocus::Abi => {
                self.library.abi_scroll = (self.library.abi_scroll as isize + delta).max(0) as usize;
            }
            _ => {}
        }
    }

    fn run_library(&mut self, task: String, module: &Module, kind: LibraryJob) {
        let job = self.run_gradle(vec![task]);
        self.library.pending = Some((job, module.path.clone(), kind));
    }

    /// To be called when a build finishes: reads what a check, an update or a dry run of this tab left.
    pub(crate) fn on_library_job(&mut self, job: JobId, outcome: &Outcome) {
        let Some((pending, path, kind)) = self.library.pending.take() else { return };
        if pending != job {
            self.library.pending = Some((pending, path, kind));
            return;
        }
        self.refresh_library();
        if kind == LibraryJob::Publish
            && *outcome == Outcome::Succeeded
            && let (Some(publication), Some(repository)) =
                (self.project.module(&path).and_then(|m| m.publication.clone()), library::maven_local())
        {
            let files = library::local_artifacts(
                &repository,
                &publication.group,
                &publication.artifact_id,
                &publication.version,
            );
            self.library.published = Some(files);
        }
    }
}
