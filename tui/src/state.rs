//! What lvm-tui remembers about each build between runs, kept in the user's data directory rather than in the build.

use std::fs;
use std::path::{Path, PathBuf};

use directories::ProjectDirs;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct State {
    pub tab: Option<String>,
    pub module: Option<String>,
}

impl State {
    /// The state of the build at [root], or the default one when there is none or it can't be read.
    pub fn load(root: &Path) -> Self {
        file(root)
            .and_then(|file| fs::read_to_string(file).ok())
            .and_then(|text| serde_json::from_str(&text).ok())
            .unwrap_or_default()
    }

    /// Saves the state; failing to is not worth bothering the user about.
    pub fn save(&self, root: &Path) {
        let Some(file) = file(root) else { return };
        if let Some(parent) = file.parent() {
            let _ = fs::create_dir_all(parent);
        }
        if let Ok(text) = serde_json::to_string_pretty(self) {
            let _ = fs::write(file, text);
        }
    }
}

fn file(root: &Path) -> Option<PathBuf> {
    let dirs = ProjectDirs::from("tech", "annexflow", "lvm-tui")?;
    Some(dirs.data_dir().join("projects").join(format!("{:016x}.json", fnv1a(root.to_string_lossy().as_bytes()))))
}

/// A stable hash of the build's path, so each build has its own file. `DefaultHasher` may change between Rust releases.
fn fnv1a(bytes: &[u8]) -> u64 {
    bytes.iter().fold(0xcbf2_9ce4_8422_2325, |hash, &byte| (hash ^ u64::from(byte)).wrapping_mul(0x0100_0000_01b3))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_hash_is_stable_and_tells_paths_apart() {
        assert_eq!(fnv1a(b""), 0xcbf2_9ce4_8422_2325);
        assert_ne!(fnv1a(b"/work/a"), fnv1a(b"/work/b"));
    }
}
