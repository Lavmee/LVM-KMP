//! Embeds what the lvm command knows about the LVM release it ships with: its version, the Gradle version it was tested
//! with, its libraries, the keys of `lvm { }` and the Gradle wrapper that new builds get.
//!
//! The files come from the LVM repository around this crate, so a build from a checkout or `cargo install --git` always
//! matches the plugins of the same commit. The package on crates.io holds only `tui/`, so `embed-files.sh` copies the
//! files into `embedded/` before it is packaged, and a build from the package reads them there.

use std::fs;
use std::path::{Path, PathBuf};

/// Each file by its name in `embedded/` and in `OUT_DIR`, and its path in the repository, relative to this crate.
const FILES: [(&str, &str); 7] = [
    ("gradle.properties", "../gradle.properties"),
    ("settings.gradle.kts", "../settings.gradle.kts"),
    ("gradle-wrapper.properties", "../gradle/wrapper/gradle-wrapper.properties"),
    ("gradle-wrapper.jar", "../gradle/wrapper/gradle-wrapper.jar"),
    ("gradlew", "../gradlew"),
    ("gradlew.bat", "../gradlew.bat"),
    ("lvm-keys.json", "../blueprint/common/lvm-keys.json"),
];

fn main() {
    let out = PathBuf::from(std::env::var_os("OUT_DIR").expect("Cargo sets OUT_DIR"));
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=embedded");
    for (name, in_repository) in FILES {
        let from = source(name, in_repository);
        println!("cargo:rerun-if-changed={}", from.display());
        fs::copy(&from, out.join(name)).unwrap_or_else(|error| panic!("Cannot copy {}: {error}", from.display()));
    }

    let version = property(&out.join("gradle.properties"), "VERSION_NAME");
    let distribution = property(&out.join("gradle-wrapper.properties"), "distributionUrl");
    let gradle = distribution
        .rsplit('/')
        .next()
        .and_then(|file| file.strip_prefix("gradle-"))
        .and_then(|rest| rest.split_once("-bin").or_else(|| rest.split_once("-all")))
        .map(|(version, _)| version.to_owned())
        .unwrap_or_else(|| panic!("Cannot read the Gradle version from {distribution}"));

    println!("cargo:rustc-env=LVM_VERSION={version}");
    println!("cargo:rustc-env=LVM_TESTED_GRADLE={gradle}");
    println!("cargo:rustc-env=LVM_LIBRARIES={}", libraries(&out.join("settings.gradle.kts")).join(","));
}

/// The file in the repository when the crate is built inside it, otherwise its copy in `embedded/`. The repository
/// wins, so a copy left in `embedded/` never hides a change to the original.
fn source(name: &str, in_repository: &str) -> PathBuf {
    let original = Path::new(in_repository);
    if original.is_file() {
        return original.to_owned();
    }
    let copy = Path::new("embedded").join(name);
    if copy.is_file() {
        return copy;
    }
    panic!("Found neither {in_repository} nor embedded/{name}: build inside the LVM repository, or run embed-files.sh");
}

/// LVM's libraries: one module in runtime/ per artifact, named lvm-<module>.
fn libraries(settings: &Path) -> Vec<String> {
    let text =
        fs::read_to_string(settings).unwrap_or_else(|error| panic!("Cannot read {}: {error}", settings.display()));
    text.lines()
        .filter_map(|line| {
            line.trim().strip_prefix("include(\":runtime:")?.split('"').next().map(|name| format!("lvm-{name}"))
        })
        .collect()
}

fn property(file: &Path, key: &str) -> String {
    let text = fs::read_to_string(file).unwrap_or_else(|error| panic!("Cannot read {}: {error}", file.display()));
    text.lines()
        .filter_map(|line| line.split_once('='))
        .find(|(name, _)| name.trim() == key)
        .map(|(_, value)| value.trim().replace("\\:", ":"))
        .unwrap_or_else(|| panic!("{key} is missing from {}", file.display()))
}
