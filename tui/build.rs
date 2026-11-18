//! Embeds what lvm-tui knows about the LVM release it ships with: its version and the Gradle version it was tested
//! with. Both come from the LVM repository around this crate, so a build from a checkout or `cargo install --git`
//! always matches the plugins of the same commit.

use std::fs;
use std::path::Path;

fn main() {
    let gradle_properties = Path::new("../gradle.properties");
    let wrapper_properties = Path::new("../gradle/wrapper/gradle-wrapper.properties");
    println!("cargo:rerun-if-changed={}", gradle_properties.display());
    println!("cargo:rerun-if-changed={}", wrapper_properties.display());

    let version = property(gradle_properties, "VERSION_NAME");
    let distribution = property(wrapper_properties, "distributionUrl");
    let gradle = distribution
        .rsplit('/')
        .next()
        .and_then(|file| file.strip_prefix("gradle-"))
        .and_then(|rest| rest.split_once("-bin").or_else(|| rest.split_once("-all")))
        .map(|(version, _)| version.to_owned())
        .unwrap_or_else(|| panic!("Cannot read the Gradle version from {distribution}"));

    println!("cargo:rustc-env=LVM_VERSION={version}");
    println!("cargo:rustc-env=LVM_TESTED_GRADLE={gradle}");
}

fn property(file: &Path, key: &str) -> String {
    let text = fs::read_to_string(file).unwrap_or_else(|error| panic!("Cannot read {}: {error}", file.display()));
    text.lines()
        .filter_map(|line| line.split_once('='))
        .find(|(name, _)| name.trim() == key)
        .map(|(_, value)| value.trim().replace("\\:", ":"))
        .unwrap_or_else(|| panic!("{key} is missing from {}", file.display()))
}
