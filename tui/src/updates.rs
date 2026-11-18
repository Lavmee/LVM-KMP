//! The Updates tab: LVM's releases on Maven Central, what each changes for the build, and the upgrade to one.

use std::collections::BTreeMap;
use std::sync::mpsc::Sender;
use std::thread;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::widgets::ListState;

use crate::app::{AfterApply, App};
use crate::event::Message;
use crate::maven::{Catalog, Releases};
use crate::upgrade;

#[derive(Default)]
pub struct UpdatesView {
    /// The released versions, newest first, once Maven Central answered.
    pub versions: Option<Result<Vec<String>, String>>,
    pub catalogs: BTreeMap<String, Result<Catalog, String>>,
    pub list: ListState,
    /// Whether the releases were asked for, so opening the tab again doesn't ask again.
    pub requested: bool,
}

impl UpdatesView {
    pub fn selected_version(&self) -> Option<&str> {
        let versions = self.versions.as_ref()?.as_ref().ok()?;
        versions.get(self.list.selected().unwrap_or(0)).map(String::as_str)
    }
}

/// Asks Maven Central for the releases, then for the catalogs of [current] and of the latest, on another thread.
fn spawn_releases(sender: Sender<Message>, current: Option<String>, refresh: bool) {
    thread::spawn(move || {
        let releases = Releases::maven_central();
        let versions = releases.versions(refresh).map_err(|e| format!("{e:#}"));
        let latest = versions.as_ref().ok().and_then(|v| v.last().cloned());
        let _ = sender.send(Message::Releases(versions.map(|mut v| {
            v.reverse();
            v
        })));
        for version in [current, latest].into_iter().flatten() {
            let catalog = releases.catalog(&version).map_err(|e| format!("{e:#}"));
            let _ = sender.send(Message::Catalog { version, catalog });
        }
    });
}

fn spawn_catalog(sender: Sender<Message>, version: String) {
    thread::spawn(move || {
        let catalog = Releases::maven_central().catalog(&version).map_err(|e| format!("{e:#}"));
        let _ = sender.send(Message::Catalog { version, catalog });
    });
}

impl App {
    /// Asks for the releases the first time the tab opens, unless the app runs without the network.
    pub(crate) fn open_updates(&mut self) {
        if !self.updates.requested && self.fetch_releases {
            self.request_releases(false);
        }
    }

    fn request_releases(&mut self, refresh: bool) {
        self.updates.requested = true;
        self.updates.versions = None;
        spawn_releases(self.runner.sender(), upgrade::current_version(&self.project), refresh);
    }

    pub(crate) fn on_releases(&mut self, versions: Result<Vec<String>, String>) {
        let count = versions.as_ref().map_or(0, Vec::len);
        self.updates.list.select((count > 0).then_some(0));
        self.updates.versions = Some(versions);
    }

    pub(crate) fn on_catalog(&mut self, version: String, catalog: Result<Catalog, String>) {
        self.updates.catalogs.insert(version, catalog);
    }

    pub(crate) fn on_updates_key(&mut self, key: KeyEvent) {
        let count = self.updates.versions.as_ref().and_then(|v| v.as_ref().ok()).map_or(0, Vec::len);
        match key.code {
            KeyCode::Down | KeyCode::Char('j') if count > 0 => {
                self.updates.list.select(Some(self.updates.list.selected().map_or(0, |i| (i + 1).min(count - 1))));
                self.load_selected_catalog();
            }
            KeyCode::Up | KeyCode::Char('k') if count > 0 => {
                self.updates.list.select(Some(self.updates.list.selected().map_or(0, |i| i.saturating_sub(1))));
                self.load_selected_catalog();
            }
            KeyCode::Char('f') => {
                if self.fetch_releases {
                    self.updates.catalogs.clear();
                    self.request_releases(true);
                }
            }
            KeyCode::Char('u') | KeyCode::Enter => self.perform(crate::app::Action::UpgradeLvm),
            _ => {}
        }
    }

    fn load_selected_catalog(&mut self) {
        let Some(version) = self.updates.selected_version().map(str::to_owned) else { return };
        if self.fetch_releases && !self.updates.catalogs.contains_key(&version) {
            spawn_catalog(self.runner.sender(), version);
        }
    }

    pub(crate) fn upgrade_selected(&mut self) {
        let Some(version) = self.updates.selected_version().map(str::to_owned) else {
            self.status = Some("No release to move to yet: the list comes from Maven Central".to_owned());
            return;
        };
        match upgrade::upgrade(&self.project, &version) {
            Ok(changes) if changes.is_empty() => {
                self.status = Some(format!("Nothing to change: the build names no LVM version other than {version}"))
            }
            Ok(changes) => self.confirm(format!("Upgrade LVM to {version}"), changes, AfterApply::OfferBuild),
            Err(error) => self.status = Some(format!("{error:#}")),
        }
    }
}
