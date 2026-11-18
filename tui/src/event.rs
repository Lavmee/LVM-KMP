//! Everything the app reacts to arrives as a [Message] on one channel: keys, ticks and Gradle output.

use std::sync::mpsc::Sender;
use std::thread;
use std::time::Duration;

use ratatui::crossterm::event::{self, Event};

use crate::doctor::Environment;
use crate::gradle::GradleEvent;

#[derive(Debug, Clone)]
pub enum Message {
    Input(Event),
    /// Sent while no input arrives, so spinners move.
    Tick,
    Gradle(GradleEvent),
    /// What the machine offers, probed in the background at start.
    Environment(Environment),
    /// LVM's releases on Maven Central, newest first.
    Releases(Result<Vec<String>, String>),
    /// The `lvm-versions` catalog of a release.
    Catalog {
        version: String,
        catalog: Result<crate::maven::Catalog, String>,
    },
}

impl From<GradleEvent> for Message {
    fn from(event: GradleEvent) -> Self {
        Self::Gradle(event)
    }
}

const TICK: Duration = Duration::from_millis(200);

/// Probes the machine on its own thread, since it runs a few commands.
pub fn spawn_environment(root: std::path::PathBuf, sender: Sender<Message>) {
    thread::spawn(move || {
        let _ = sender.send(Message::Environment(Environment::probe(&root)));
    });
}

/// Reads terminal input on its own thread until the receiver is gone.
pub fn spawn_input(sender: Sender<Message>) {
    thread::spawn(move || {
        loop {
            let message = match event::poll(TICK) {
                Ok(true) => match event::read() {
                    Ok(event) => Message::Input(event),
                    Err(_) => return,
                },
                Ok(false) => Message::Tick,
                Err(_) => return,
            };
            if sender.send(message).is_err() {
                return;
            }
        }
    });
}
