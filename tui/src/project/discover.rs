//! Finds the root of the Gradle build that a directory belongs to.

use std::path::{Path, PathBuf};

use super::settings::settings_file;

/// The nearest directory at or above [start] with a settings script.
pub fn find_root(start: &Path) -> Option<PathBuf> {
    let start = start.canonicalize().ok()?;
    start.ancestors().find(|dir| settings_file(dir).is_some()).map(Path::to_path_buf)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn walks_up_to_the_settings_script() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path().canonicalize().unwrap();
        std::fs::write(root.join("settings.gradle.kts"), "").unwrap();
        std::fs::create_dir_all(root.join("feature/chat/src")).unwrap();
        assert_eq!(find_root(&root.join("feature/chat/src")), Some(root.clone()));
        assert_eq!(find_root(&root), Some(root));
    }
}
