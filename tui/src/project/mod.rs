//! Reading a Gradle build that uses LVM: its scripts, its properties and its `lvmReport` reports.

pub mod catalog;
pub mod discover;
pub mod keys;
pub mod kts;
pub mod lvm_block;
pub mod model;
pub mod module;
pub mod properties;
pub mod report;
pub mod settings;
pub mod wrapper;

pub use model::Project;
