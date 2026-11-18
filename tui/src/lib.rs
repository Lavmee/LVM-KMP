//! lvm-tui: a terminal app for Gradle builds that use LVM's convention plugins.

pub mod app;
pub mod check;
pub mod doctor;
pub mod edit;
pub mod event;
pub mod gradle;
pub mod migrate;
pub mod project;
pub mod results;
pub mod state;
pub mod tasks;
pub mod ui;
pub mod version;

/// The LVM release that this lvm-tui ships with.
pub const LVM_VERSION: &str = env!("LVM_VERSION");

/// The Gradle version that this LVM release was tested with.
pub const TESTED_GRADLE: &str = env!("LVM_TESTED_GRADLE");
