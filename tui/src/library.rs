//! What a published module needs: an ABI dump that matches its code, a complete POM, signing for a release, and
//! artifacts that Maven local shows before Maven Central does.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::SystemTime;

use similar::{ChangeTag, TextDiff};

use crate::project::model::Module;
use crate::project::properties::Properties;
use crate::project::report::Source;

/// One dump file of a module: `api/<path>` against what Kotlin generated in `build/kotlin/abi/<path>`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DumpDiff {
    /// The path below `api/`, such as `jvm/core.api` or `core.klib.api`.
    pub path: PathBuf,
    /// Lines of the generated dump that `api/` lacks: new public API.
    pub added: usize,
    /// Lines of `api/` that the generated dump lacks: public API that went away, which breaks consumers.
    pub removed: usize,
    /// The unified diff from `api/` to the generated dump.
    pub diff: Vec<String>,
}

/// How the module's public API compares with its committed dump.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AbiState {
    /// `checkKotlinAbi` hasn't generated a dump yet.
    NotChecked,
    /// The generated dump matches `api/`.
    Matches {
        checked: Option<SystemTime>,
    },
    Changed(Vec<DumpDiff>),
}

/// Compares `api/` with the dump that the last `checkKotlinAbi` or `updateKotlinAbi` generated.
pub fn abi_state(module_dir: &Path) -> AbiState {
    let committed = module_dir.join("api");
    let generated = module_dir.join("build/kotlin/abi");
    let mut files = Vec::new();
    collect(&generated, &generated, &mut files);
    collect(&committed, &committed, &mut files);
    files.sort();
    files.dedup();
    if !generated.is_dir() {
        return AbiState::NotChecked;
    }
    let checked = files.iter().filter_map(|f| fs::metadata(generated.join(f)).and_then(|m| m.modified()).ok()).max();
    let diffs: Vec<DumpDiff> = files
        .into_iter()
        .filter_map(|path| {
            let old = fs::read_to_string(committed.join(&path)).unwrap_or_default();
            let new = fs::read_to_string(generated.join(&path)).unwrap_or_default();
            (old != new).then(|| diff(path, &old, &new))
        })
        .collect();
    if diffs.is_empty() { AbiState::Matches { checked } } else { AbiState::Changed(diffs) }
}

fn diff(path: PathBuf, old: &str, new: &str) -> DumpDiff {
    let text = TextDiff::from_lines(old, new);
    let count = |tag| text.iter_all_changes().filter(|c| c.tag() == tag && !c.value().trim().is_empty()).count();
    let name = path.display().to_string();
    let unified =
        text.unified_diff().context_radius(2).header(&format!("api/{name}"), &format!("generated {name}")).to_string();
    DumpDiff {
        added: count(ChangeTag::Insert),
        removed: count(ChangeTag::Delete),
        diff: unified.lines().map(str::to_owned).collect(),
        path,
    }
}

fn collect(base: &Path, dir: &Path, files: &mut Vec<PathBuf>) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for entry in entries.filter_map(Result::ok) {
        let path = entry.path();
        if path.is_dir() {
            collect(base, &path, files);
        } else if path.extension().is_some_and(|ext| ext == "api") {
            files.push(path.strip_prefix(base).unwrap_or(&path).to_owned());
        }
    }
}

/// `git diff` of the module's `api/`: what `updateKotlinAbi` changed and nobody committed yet.
pub fn uncommitted_dump(root: &Path, module_dir: &Path) -> Vec<String> {
    let api = module_dir.join("api");
    let output = Command::new("git").args(["diff", "--no-color", "--"]).arg(&api).current_dir(root).output();
    match output {
        Ok(output) if output.status.success() => {
            String::from_utf8_lossy(&output.stdout).lines().map(str::to_owned).collect()
        }
        _ => Vec::new(),
    }
}

/// POM values that Maven Central requires, which blueprint fails without.
pub const REQUIRED_POM: &[&str] = &[
    "publish.description",
    "publish.url",
    "publish.license.name",
    "publish.license.url",
    "publish.developer.id",
    "publish.developer.name",
];

/// The POM values of [module], required ones first: key, value, source.
pub fn pom(module: &Module) -> Vec<(String, Option<String>, Source)> {
    let mut rows: Vec<(String, Option<String>, Source)> = module
        .values
        .iter()
        .filter(|row| row.key.starts_with("publish.") && !row.key.starts_with("publish.abi."))
        .map(|row| {
            let value = row.value.as_ref().map(|v| v.as_str().map_or_else(|| v.to_string(), str::to_owned));
            (row.key.clone(), value.filter(|v| !v.is_empty()), row.source)
        })
        .collect();
    rows.sort_by_key(|(key, _, _)| {
        (!REQUIRED_POM.contains(&key.as_str()), REQUIRED_POM.iter().position(|k| k == key), key.clone())
    });
    rows
}

/// The properties that vanniktech's plugin reads to upload and sign.
pub const SECRETS: &[&str] = &[
    "mavenCentralUsername",
    "mavenCentralPassword",
    "signingInMemoryKey",
    "signingInMemoryKeyId",
    "signingInMemoryKeyPassword",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Place {
    /// `~/.gradle/gradle.properties`, outside every build.
    UserProperties,
    /// An `ORG_GRADLE_PROJECT_<name>` environment variable, as CI sets them.
    Environment,
    /// The build's own `gradle.properties`, which git may share.
    BuildProperties,
}

impl Place {
    pub fn label(self) -> &'static str {
        match self {
            Self::UserProperties => "~/.gradle",
            Self::Environment => "environment",
            Self::BuildProperties => "the build!",
        }
    }
}

/// Where each secret is set. Only whether it is set: lvm-tui never reads a secret's value into its own state.
pub fn secrets(build: &Properties, user_home: Option<&Path>) -> Vec<(&'static str, Vec<Place>)> {
    let user = user_home.and_then(|home| Properties::read(&home.join("gradle.properties")));
    SECRETS
        .iter()
        .map(|key| {
            let mut places = Vec::new();
            if user.as_ref().is_some_and(|p| p.get(key).is_some_and(|v| !v.is_empty())) {
                places.push(Place::UserProperties);
            }
            if std::env::var_os(format!("ORG_GRADLE_PROJECT_{key}")).is_some_and(|v| !v.is_empty()) {
                places.push(Place::Environment);
            }
            if build.get(key).is_some_and(|v| !v.is_empty()) {
                places.push(Place::BuildProperties);
            }
            (*key, places)
        })
        .collect()
}

/// The Gradle user home: `GRADLE_USER_HOME`, else `~/.gradle`.
pub fn gradle_user_home() -> Option<PathBuf> {
    std::env::var_os("GRADLE_USER_HOME")
        .map(PathBuf::from)
        .or_else(|| directories::BaseDirs::new().map(|dirs| dirs.home_dir().join(".gradle")))
}

/// The files that `publishToMavenLocal` left for [group]:[artifact]:[version], for the artifact and its platform
/// artifacts such as `-jvm` and `-android`.
pub fn local_artifacts(repository: &Path, group: &str, artifact: &str, version: &str) -> Vec<PathBuf> {
    let group_dir = repository.join(group.replace('.', "/"));
    let mut files = Vec::new();
    for entry in fs::read_dir(&group_dir).into_iter().flatten().filter_map(Result::ok) {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name != artifact && !name.starts_with(&format!("{artifact}-")) {
            continue;
        }
        let dir = entry.path().join(version);
        let mut names: Vec<PathBuf> =
            fs::read_dir(&dir).into_iter().flatten().filter_map(Result::ok).map(|e| e.path()).collect();
        names.retain(|p| !p.file_name().is_some_and(|n| n.to_string_lossy().starts_with("maven-metadata")));
        files.extend(names);
    }
    files.sort();
    files
}

/// Maven local: `~/.m2/repository`.
pub fn maven_local() -> Option<PathBuf> {
    directories::BaseDirs::new().map(|dirs| dirs.home_dir().join(".m2/repository"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write(dir: &Path, file: &str, text: &str) {
        let path = dir.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    #[test]
    fn compares_every_dump_with_the_generated_one() {
        let dir = tempfile::tempdir().unwrap();
        let module = dir.path();
        assert_eq!(abi_state(module), AbiState::NotChecked);
        write(module, "api/jvm/core.api", "public final class A {\n\tpublic fun a ()V\n\tpublic fun b ()V\n}\n");
        write(module, "api/core.klib.api", "same\n");
        write(
            module,
            "build/kotlin/abi/jvm/core.api",
            "public final class A {\n\tpublic fun a ()V\n\tpublic fun c ()V\n\tpublic fun d ()V\n}\n",
        );
        write(module, "build/kotlin/abi/core.klib.api", "same\n");

        let AbiState::Changed(diffs) = abi_state(module) else { panic!("the jvm dump changed") };
        assert_eq!(diffs.len(), 1);
        assert_eq!(diffs[0].path, PathBuf::from("jvm/core.api"));
        assert_eq!((diffs[0].added, diffs[0].removed), (2, 1));
        assert!(diffs[0].diff.contains(&"-\tpublic fun b ()V".to_owned()), "{:?}", diffs[0].diff);

        write(
            module,
            "api/jvm/core.api",
            "public final class A {\n\tpublic fun a ()V\n\tpublic fun c ()V\n\tpublic fun d ()V\n}\n",
        );
        assert!(matches!(abi_state(module), AbiState::Matches { .. }));
    }

    #[test]
    fn a_dump_that_appeared_or_went_away_counts_as_changed() {
        let dir = tempfile::tempdir().unwrap();
        write(dir.path(), "build/kotlin/abi/android/core.api", "public final class A {\n}\n");
        let AbiState::Changed(diffs) = abi_state(dir.path()) else { panic!("a new dump") };
        assert_eq!((diffs[0].added, diffs[0].removed), (2, 0));
    }

    #[test]
    fn finds_secrets_without_keeping_their_values() {
        let home = tempfile::tempdir().unwrap();
        write(home.path(), "gradle.properties", "mavenCentralUsername=me\nmavenCentralPassword=\n");
        let build = Properties::parse("signingInMemoryKey=-----BEGIN PGP\n");
        let found = secrets(&build, Some(home.path()));
        let places = |key: &str| found.iter().find(|(k, _)| *k == key).map(|(_, p)| p.clone()).unwrap();
        assert_eq!(places("mavenCentralUsername"), [Place::UserProperties]);
        assert!(places("mavenCentralPassword").is_empty(), "an empty value is not set");
        assert_eq!(places("signingInMemoryKey"), [Place::BuildProperties]);
    }

    #[test]
    fn lists_the_artifacts_of_a_publication_and_its_platforms() {
        let repository = tempfile::tempdir().unwrap();
        for file in [
            "tech/annexflow/lvm/lvm-core/1.0/lvm-core-1.0.module",
            "tech/annexflow/lvm/lvm-core/maven-metadata-local.xml",
            "tech/annexflow/lvm/lvm-core-jvm/1.0/lvm-core-jvm-1.0.jar",
            "tech/annexflow/lvm/lvm-core-jvm/0.9/lvm-core-jvm-0.9.jar",
            "tech/annexflow/lvm/lvm-coroutines/1.0/lvm-coroutines-1.0.module",
        ] {
            write(repository.path(), file, "");
        }
        let files = local_artifacts(repository.path(), "tech.annexflow.lvm", "lvm-core", "1.0");
        let names: Vec<_> = files.iter().map(|f| f.file_name().unwrap().to_string_lossy().into_owned()).collect();
        assert_eq!(names, ["lvm-core-1.0.module", "lvm-core-jvm-1.0.jar"]);
    }
}
