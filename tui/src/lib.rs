//! lvm-tui: a terminal app for Gradle builds that use LVM's convention plugins.

pub mod app;
pub mod applog;
pub mod check;
pub mod compose;
pub mod compose_tab;
pub mod devices;
pub mod doctor;
pub mod edit;
pub mod event;
pub mod form;
pub mod gradle;
pub mod library;
pub mod library_tab;
pub mod maven;
pub mod migrate;
pub mod project;
pub mod results;
pub mod run;
pub mod run_tab;
pub mod scaffold;
pub mod state;
pub mod tasks;
pub mod ui;
pub mod updates;
pub mod upgrade;
pub mod version;
pub mod wizard;

/// The LVM release that this lvm-tui ships with.
pub const LVM_VERSION: &str = env!("LVM_VERSION");

/// The Gradle version that this LVM release was tested with.
pub const TESTED_GRADLE: &str = env!("LVM_TESTED_GRADLE");
