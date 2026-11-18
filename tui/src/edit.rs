//! Changes to a build: new file contents and moved files, which the user sees as a diff before they are applied.

use std::collections::BTreeMap;
use std::fs;
use std::ops::Range;
use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{Context, Result, bail};
use serde::Serialize;
use similar::TextDiff;

/// A replacement of the bytes [range] of a text.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TextEdit {
    pub range: Range<usize>,
    pub text: String,
}

impl TextEdit {
    pub fn insert(at: usize, text: impl Into<String>) -> Self {
        Self { range: at..at, text: text.into() }
    }

    pub fn replace(range: Range<usize>, text: impl Into<String>) -> Self {
        Self { range, text: text.into() }
    }
}

/// Applies [edits] to [source]. Edits must not overlap; their order doesn't matter.
pub fn apply_edits(source: &str, edits: &[TextEdit]) -> String {
    let mut sorted: Vec<&TextEdit> = edits.iter().collect();
    sorted.sort_by_key(|edit| (edit.range.start, edit.range.end));
    let mut result = String::with_capacity(source.len());
    let mut position = 0;
    for edit in sorted {
        let start = edit.range.start.max(position);
        result.push_str(&source[position..start]);
        result.push_str(&edit.text);
        position = edit.range.end.max(start);
    }
    result.push_str(&source[position..]);
    result
}

/// What a change does, for the user and for `lvm-tui check --json`.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
pub struct ChangeSet {
    /// New contents by file, relative to the build root. A file that doesn't exist yet starts empty.
    pub files: BTreeMap<PathBuf, FileChange>,
    /// Files to move, relative to the build root, with `git mv` where git tracks them.
    pub moves: Vec<Move>,
    /// Files to remove from git's index while keeping them on disk.
    pub untrack: Vec<PathBuf>,
    /// New binary files, such as the Gradle wrapper's jar, by path.
    #[serde(skip)]
    pub binaries: BTreeMap<PathBuf, Vec<u8>>,
    /// Files to make executable, such as `gradlew`.
    pub executables: Vec<PathBuf>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct FileChange {
    #[serde(skip)]
    pub before: String,
    #[serde(skip)]
    pub after: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Move {
    pub from: PathBuf,
    pub to: PathBuf,
}

impl ChangeSet {
    pub fn is_empty(&self) -> bool {
        self.files.values().all(|change| change.before == change.after)
            && self.moves.is_empty()
            && self.untrack.is_empty()
            && self.binaries.is_empty()
    }

    /// The current text of [file]: as an earlier edit of this change left it, or as it is on disk.
    pub fn current(&self, root: &Path, file: &Path) -> String {
        match self.files.get(file) {
            Some(change) => change.after.clone(),
            None => fs::read_to_string(root.join(file)).unwrap_or_default(),
        }
    }

    /// Applies [edits] to the current text of [file].
    pub fn edit(&mut self, root: &Path, file: &Path, edits: &[TextEdit]) {
        let current = self.current(root, file);
        let after = apply_edits(&current, edits);
        self.set(root, file, after);
    }

    /// Replaces the text of [file].
    pub fn set(&mut self, root: &Path, file: &Path, after: String) {
        let before = match self.files.get(file) {
            Some(change) => change.before.clone(),
            None => fs::read_to_string(root.join(file)).unwrap_or_default(),
        };
        self.files.insert(file.to_owned(), FileChange { before, after });
    }

    /// A unified diff of every file, followed by the moves.
    pub fn preview(&self) -> String {
        let mut text = String::new();
        for (file, change) in &self.files {
            if change.before == change.after {
                continue;
            }
            let name = file.display().to_string();
            let diff = TextDiff::from_lines(&change.before, &change.after);
            let before = if change.before.is_empty() { "/dev/null".to_owned() } else { format!("a/{name}") };
            text.push_str(&diff.unified_diff().context_radius(2).header(&before, &format!("b/{name}")).to_string());
            if !text.ends_with('\n') {
                text.push('\n');
            }
        }
        for (file, bytes) in &self.binaries {
            text.push_str(&format!("new binary file {} ({} bytes)\n", file.display(), bytes.len()));
        }
        for change in &self.moves {
            text.push_str(&format!("move {} -> {}\n", change.from.display(), change.to.display()));
        }
        for file in &self.untrack {
            text.push_str(&format!("git rm --cached {}\n", file.display()));
        }
        text
    }

    /// Writes the files, then moves and untracks. Stops at the first failure.
    pub fn apply(&self, root: &Path) -> Result<()> {
        for change in &self.moves {
            if root.join(&change.to).exists() {
                bail!("{} already exists", change.to.display());
            }
        }
        for (file, change) in &self.files {
            if change.before == change.after {
                continue;
            }
            let path = root.join(file);
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::write(&path, &change.after).with_context(|| format!("writing {}", file.display()))?;
        }
        for (file, bytes) in &self.binaries {
            let path = root.join(file);
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::write(&path, bytes).with_context(|| format!("writing {}", file.display()))?;
        }
        for file in &self.executables {
            make_executable(&root.join(file))?;
        }
        let git = is_git_work_tree(root);
        for change in &self.moves {
            move_file(root, change, git)?;
        }
        for file in &self.untrack {
            run_git(root, &["rm", "--cached", "--quiet", "--", &file.to_string_lossy()])?;
        }
        remove_empty_directories(root, self.moves.iter().map(|m| m.from.as_path()));
        Ok(())
    }
}

#[cfg(unix)]
fn make_executable(path: &Path) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    let mut permissions = fs::metadata(path)?.permissions();
    permissions.set_mode(permissions.mode() | 0o111);
    fs::set_permissions(path, permissions).with_context(|| format!("making {} executable", path.display()))
}

#[cfg(not(unix))]
fn make_executable(_: &Path) -> Result<()> {
    Ok(())
}

pub fn is_git_work_tree(root: &Path) -> bool {
    Command::new("git")
        .args(["rev-parse", "--is-inside-work-tree"])
        .current_dir(root)
        .output()
        .is_ok_and(|output| output.status.success() && output.stdout.starts_with(b"true"))
}

/// Whether git tracks [file] in the work tree at [root].
pub fn is_tracked(root: &Path, file: &Path) -> bool {
    Command::new("git")
        .args(["ls-files", "--error-unmatch", "--"])
        .arg(file)
        .current_dir(root)
        .output()
        .is_ok_and(|output| output.status.success())
}

fn move_file(root: &Path, change: &Move, git: bool) -> Result<()> {
    let to = root.join(&change.to);
    if let Some(parent) = to.parent() {
        fs::create_dir_all(parent)?;
    }
    // git mv keeps the file's history connected; an untracked file is moved like any other.
    if git && is_tracked(root, &change.from) {
        run_git(root, &["mv", "--", &change.from.to_string_lossy(), &change.to.to_string_lossy()])
    } else {
        fs::rename(root.join(&change.from), to).with_context(|| format!("moving {}", change.from.display()))
    }
}

fn run_git(root: &Path, args: &[&str]) -> Result<()> {
    let output = Command::new("git").args(args).current_dir(root).output().context("running git")?;
    if !output.status.success() {
        bail!("git {}: {}", args.join(" "), String::from_utf8_lossy(&output.stderr).trim());
    }
    Ok(())
}

/// Removes the directories that moving files out of left empty, up to the build root.
fn remove_empty_directories<'a>(root: &Path, moved: impl Iterator<Item = &'a Path>) {
    let mut directories: Vec<PathBuf> = moved.filter_map(|file| file.parent().map(Path::to_path_buf)).collect();
    directories.sort_by_key(|dir| std::cmp::Reverse(dir.components().count()));
    directories.dedup();
    for dir in directories {
        let mut current = Some(dir.as_path());
        while let Some(dir) = current.filter(|dir| !dir.as_os_str().is_empty()) {
            if fs::remove_dir(root.join(dir)).is_err() {
                break;
            }
            current = dir.parent();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn applies_edits_in_any_order() {
        let edits = [TextEdit::replace(6..11, "there"), TextEdit::insert(0, ">> "), TextEdit::insert(11, "!")];
        assert_eq!(apply_edits("hello world", &edits), ">> hello there!");
    }

    #[test]
    fn previews_new_and_changed_files_and_moves() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("a.txt"), "one\ntwo\n").unwrap();
        let mut change = ChangeSet::default();
        change.edit(dir.path(), Path::new("a.txt"), &[TextEdit::replace(4..7, "2")]);
        change.set(dir.path(), Path::new("new.txt"), "new\n".to_owned());
        change.moves.push(Move { from: "x/A.kt".into(), to: "A.kt".into() });

        let preview = change.preview();

        assert!(preview.contains("--- a/a.txt\n+++ b/a.txt\n"), "{preview}");
        assert!(preview.contains("-two\n+2\n"), "{preview}");
        assert!(preview.contains("--- /dev/null\n+++ b/new.txt\n"), "{preview}");
        assert!(preview.ends_with("move x/A.kt -> A.kt\n"), "{preview}");
    }

    #[test]
    fn applies_files_and_moves_and_removes_emptied_directories() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        fs::create_dir_all(root.join("src/commonMain/kotlin/com/example")).unwrap();
        fs::write(root.join("src/commonMain/kotlin/com/example/A.kt"), "class A").unwrap();
        let mut change = ChangeSet::default();
        change.moves.push(Move { from: "src/commonMain/kotlin/com/example/A.kt".into(), to: "src/A.kt".into() });
        change.set(root, Path::new("gradle.properties"), "lvm.kotlin.flatLayout=true\n".to_owned());

        change.apply(root).unwrap();

        assert_eq!(fs::read_to_string(root.join("src/A.kt")).unwrap(), "class A");
        assert!(!root.join("src/commonMain").exists());
        assert!(root.join("src").is_dir());
        assert_eq!(fs::read_to_string(root.join("gradle.properties")).unwrap(), "lvm.kotlin.flatLayout=true\n");
    }

    #[test]
    fn refuses_to_move_onto_an_existing_file() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("a"), "").unwrap();
        fs::write(dir.path().join("b"), "").unwrap();
        let change = ChangeSet { moves: vec![Move { from: "a".into(), to: "b".into() }], ..ChangeSet::default() };
        assert!(change.apply(dir.path()).is_err());
    }

    #[test]
    fn later_edits_build_on_earlier_ones() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("f"), "a\n").unwrap();
        let mut change = ChangeSet::default();
        change.edit(dir.path(), Path::new("f"), &[TextEdit::insert(2, "b\n")]);
        change.edit(dir.path(), Path::new("f"), &[TextEdit::insert(4, "c\n")]);
        assert_eq!(change.files[Path::new("f")], FileChange { before: "a\n".into(), after: "a\nb\nc\n".into() });
    }
}
