//! What builds leave behind: console output, test results and detekt reports.

pub mod detekt;
pub mod junit;
pub mod log;

use std::path::Path;

use crate::project::Project;

/// The test results and detekt issues of every module, as the last builds wrote them.
#[derive(Debug, Clone, Default)]
pub struct Results {
    pub tests: Vec<junit::TestCase>,
    pub issues: Vec<detekt::Issue>,
}

impl Results {
    pub fn load(project: &Project) -> Self {
        let mut results = Self::default();
        for module in &project.modules {
            let dir = project.root.join(&module.dir);
            results.tests.extend(junit::load(&module.path, &dir));
            results.issues.extend(detekt::load(&module.path, &dir));
        }
        results.tests.sort_by(|a, b| {
            (a.status, &a.module, &a.task, &a.class, &a.name).cmp(&(b.status, &b.module, &b.task, &b.class, &b.name))
        });
        results
    }

    pub fn failed(&self) -> impl Iterator<Item = &junit::TestCase> {
        self.tests.iter().filter(|test| test.status == junit::Status::Failed)
    }
}

/// The file of a module named [file_name], such as `GreetingTest.kt` from a stack trace, preferring one under the
/// package path of [class].
pub fn find_source(module_dir: &Path, file_name: &str, class: &str) -> Option<std::path::PathBuf> {
    let package: std::path::PathBuf =
        class.split('.').take_while(|part| part.starts_with(char::is_lowercase)).collect();
    let mut found = Vec::new();
    collect(module_dir, file_name, &mut found, 0);
    found.sort_by_key(|path| (!path.parent().is_some_and(|dir| dir.ends_with(&package)), path.components().count()));
    found.into_iter().next()
}

fn collect(dir: &Path, file_name: &str, found: &mut Vec<std::path::PathBuf>, depth: usize) {
    let Ok(entries) = std::fs::read_dir(dir) else { return };
    for entry in entries.filter_map(Result::ok) {
        let path = entry.path();
        let name = entry.file_name();
        if path.is_dir() {
            if depth < 12 && name != "build" && name != ".gradle" && !name.to_string_lossy().starts_with('.') {
                collect(&path, file_name, found, depth + 1);
            }
        } else if name == file_name {
            found.push(path);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_a_source_preferring_its_package_directory() {
        let dir = tempfile::tempdir().unwrap();
        for file in
            ["test/GreetingTest.kt", "src/commonTest/kotlin/com/example/GreetingTest.kt", "build/x/GreetingTest.kt"]
        {
            let path = dir.path().join(file);
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(path, "").unwrap();
        }
        let found = find_source(dir.path(), "GreetingTest.kt", "com.example.GreetingTest").unwrap();
        assert!(found.ends_with("com/example/GreetingTest.kt"), "{}", found.display());
        let flat = find_source(dir.path(), "GreetingTest.kt", "GreetingTest").unwrap();
        assert!(!flat.starts_with(dir.path().join("build")));
    }
}
