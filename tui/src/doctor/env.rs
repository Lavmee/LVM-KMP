//! What the machine offers a build: a JDK, an Android SDK, Xcode and git.

use std::path::{Path, PathBuf};
use std::process::Command;

use crate::project::properties::Properties;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Environment {
    /// `macos`, `linux` or `windows`.
    pub os: String,
    /// The major version of the JDK that `JAVA_HOME` or the `PATH` points to.
    pub java: Option<u32>,
    pub android_sdk: Option<PathBuf>,
    /// Whether `xcode-select -p` finds Xcode; only asked on macOS.
    pub xcode: Option<bool>,
    /// Whether git tracks `local.properties` of the build.
    pub local_properties_tracked: bool,
}

impl Environment {
    /// Asks the machine. This runs `java -version`, `xcode-select -p` and git, so it takes a moment.
    pub fn probe(root: &Path) -> Self {
        let os = std::env::consts::OS.to_owned();
        let xcode = (os == "macos").then(|| succeeds(Command::new("xcode-select").arg("-p")));
        Self {
            java: java_version(),
            android_sdk: android_sdk(root),
            xcode,
            local_properties_tracked: local_properties_tracked(root),
            os,
        }
    }

    /// Asks git again, after a change that may have untracked `local.properties`.
    pub fn recheck_git(&mut self, root: &Path) {
        self.local_properties_tracked = local_properties_tracked(root);
    }
}

fn local_properties_tracked(root: &Path) -> bool {
    root.join("local.properties").is_file() && crate::edit::is_tracked(root, Path::new("local.properties"))
}

fn succeeds(command: &mut Command) -> bool {
    command.output().is_ok_and(|output| output.status.success())
}

fn java_version() -> Option<u32> {
    let java = std::env::var_os("JAVA_HOME")
        .map(|home| PathBuf::from(home).join("bin").join(if cfg!(windows) { "java.exe" } else { "java" }))
        .filter(|path| path.is_file())
        .unwrap_or_else(|| PathBuf::from("java"));
    let output = Command::new(java).arg("-version").output().ok()?;
    parse_java_version(&String::from_utf8_lossy(&output.stderr))
}

/// `openjdk version "21.0.2" 2024-01-16` -> 21; `java version "1.8.0_402"` -> 8.
pub fn parse_java_version(text: &str) -> Option<u32> {
    let quoted = text.split('"').nth(1)?;
    let mut parts = quoted.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 { parts.next()?.parse().ok() } else { Some(first) }
}

/// The SDK that AGP would use: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`.
fn android_sdk(root: &Path) -> Option<PathBuf> {
    ["ANDROID_HOME", "ANDROID_SDK_ROOT"]
        .iter()
        .filter_map(std::env::var_os)
        .map(PathBuf::from)
        .chain(Properties::read(&root.join("local.properties")).and_then(|p| p.get("sdk.dir").map(PathBuf::from)))
        .find(|dir| dir.is_dir())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_modern_and_legacy_java_versions() {
        assert_eq!(parse_java_version("openjdk version \"21.0.2\" 2024-01-16\nOpenJDK Runtime"), Some(21));
        assert_eq!(parse_java_version("java version \"1.8.0_402\""), Some(8));
        assert_eq!(parse_java_version("openjdk version \"25-ea\""), Some(25));
        assert_eq!(parse_java_version("no java here"), None);
    }
}
