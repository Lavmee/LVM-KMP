//! LVM's releases on Maven Central: which versions exist, and the `lvm-versions` catalog of each, with the versions of
//! Kotlin, AGP and the rest that a release was tested with.
//!
//! A catalog that Gradle already downloaded is read from its cache. Everything fetched is kept for a day in lvm-tui's
//! cache directory, and an older copy is used while Maven Central can't be reached.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use anyhow::{Context, Result, bail};
use quick_xml::Reader;
use quick_xml::events::Event;

use crate::project::catalog;
use crate::version;

const REPOSITORY: &str = "https://repo1.maven.org/maven2/tech/annexflow/lvm/lvm-versions";
const FRESH_FOR: Duration = Duration::from_secs(24 * 60 * 60);

/// Where releases come from: Maven Central, or anything else that serves the same files, as tests do.
pub trait Source {
    fn fetch(&self, path: &str) -> Result<String>;
}

/// Maven Central over HTTPS.
pub struct MavenCentral;

impl Source for MavenCentral {
    fn fetch(&self, path: &str) -> Result<String> {
        let agent: ureq::Agent = ureq::Agent::config_builder()
            .timeout_global(Some(Duration::from_secs(20)))
            .user_agent(concat!("lvm-tui/", env!("CARGO_PKG_VERSION")))
            .build()
            .into();
        let url = format!("{REPOSITORY}/{path}");
        let mut response = agent.get(&url).call().with_context(|| format!("fetching {url}"))?;
        Ok(response.body_mut().read_to_string()?)
    }
}

/// Releases, as one source and two caches give them.
pub struct Releases<S: Source> {
    source: S,
    cache: Option<PathBuf>,
    gradle_home: Option<PathBuf>,
}

impl Releases<MavenCentral> {
    pub fn maven_central() -> Self {
        let cache =
            directories::ProjectDirs::from("tech", "annexflow", "lvm-tui").map(|dirs| dirs.cache_dir().to_owned());
        let gradle_home = std::env::var_os("GRADLE_USER_HOME")
            .map(PathBuf::from)
            .or_else(|| directories::BaseDirs::new().map(|dirs| dirs.home_dir().join(".gradle")));
        Self { source: MavenCentral, cache, gradle_home }
    }
}

/// The versions of a release's `lvm-versions` catalog, by name, such as `kotlin` or `agp`.
pub type Catalog = BTreeMap<String, String>;

impl<S: Source> Releases<S> {
    pub fn new(source: S, cache: Option<PathBuf>, gradle_home: Option<PathBuf>) -> Self {
        Self { source, cache, gradle_home }
    }

    /// The released versions, oldest first. [refresh] skips the day-old cache.
    pub fn versions(&self, refresh: bool) -> Result<Vec<String>> {
        let text = self.cached("maven-metadata.xml", refresh, true)?;
        let mut versions = parse_metadata(&text);
        versions.sort_by(|a, b| version::compare(a, b));
        if versions.is_empty() {
            bail!("Maven Central lists no release of lvm-versions");
        }
        Ok(versions)
    }

    /// The catalog of [release]. A release never changes, so a copy is kept for good.
    pub fn catalog(&self, release: &str) -> Result<Catalog> {
        if let Some(text) = self.gradle_cache(release) {
            return Ok(catalog::parse_versions(&text));
        }
        let text = self.cached(&format!("{release}/lvm-versions-{release}.toml"), false, false)?;
        Ok(catalog::parse_versions(&text))
    }

    /// The catalog that Gradle downloaded for a build that uses [release].
    fn gradle_cache(&self, release: &str) -> Option<String> {
        let dir =
            self.gradle_home.as_ref()?.join("caches/modules-2/files-2.1/tech.annexflow.lvm/lvm-versions").join(release);
        fs::read_dir(dir)
            .ok()?
            .filter_map(Result::ok)
            .map(|hash| hash.path().join(format!("lvm-versions-{release}.toml")))
            .find_map(|file| fs::read_to_string(file).ok())
    }

    /// [path] from the cache while it is fresh, else from the source; a stale copy when the source fails.
    fn cached(&self, path: &str, refresh: bool, expires: bool) -> Result<String> {
        let file = self.cache.as_ref().map(|dir| dir.join("lvm-versions").join(path));
        if let Some(file) = &file
            && !refresh
            && is_fresh(file, expires)
            && let Ok(text) = fs::read_to_string(file)
        {
            return Ok(text);
        }
        match self.source.fetch(path) {
            Ok(text) => {
                if let Some(file) = &file {
                    let _ = fs::create_dir_all(file.parent().unwrap_or(Path::new(".")));
                    let _ = fs::write(file, &text);
                }
                Ok(text)
            }
            Err(error) => match file.as_ref().and_then(|file| fs::read_to_string(file).ok()) {
                Some(stale) => Ok(stale),
                None => Err(error),
            },
        }
    }
}

fn is_fresh(file: &Path, expires: bool) -> bool {
    let Ok(modified) = fs::metadata(file).and_then(|m| m.modified()) else { return false };
    !expires || SystemTime::now().duration_since(modified).is_ok_and(|age| age < FRESH_FOR)
}

/// The `<version>` entries of Maven's `maven-metadata.xml`.
pub fn parse_metadata(xml: &str) -> Vec<String> {
    let mut reader = Reader::from_str(xml);
    let mut versions = Vec::new();
    let mut in_version = false;
    loop {
        match reader.read_event() {
            Ok(Event::Start(element)) => in_version = element.local_name().as_ref() == "version",
            Ok(Event::Text(text)) if in_version => versions.push(text.xml10_content().trim().to_owned()),
            Ok(Event::End(_)) => in_version = false,
            Ok(Event::Eof) | Err(_) => break,
            Ok(_) => {}
        }
    }
    versions
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;

    const METADATA: &str = "<metadata><versioning><latest>0.3.2</latest><versions><version>0.10.0</version><version>0.3.2</version><version>0.3.1</version></versions></versioning></metadata>";

    /// Serves fixed files and counts the requests, or fails while offline.
    struct Fake {
        files: BTreeMap<&'static str, &'static str>,
        requests: RefCell<usize>,
        offline: bool,
    }

    impl Source for &Fake {
        fn fetch(&self, path: &str) -> Result<String> {
            *self.requests.borrow_mut() += 1;
            if self.offline {
                bail!("offline");
            }
            self.files.get(path).map(|text| (*text).to_owned()).context("not found")
        }
    }

    fn fake(offline: bool) -> Fake {
        Fake {
            files: BTreeMap::from([
                ("maven-metadata.xml", METADATA),
                ("0.3.2/lvm-versions-0.3.2.toml", "[versions]\nkotlin = \"2.4.21\"\nagp = \"9.5.1\"\n"),
            ]),
            requests: RefCell::new(0),
            offline,
        }
    }

    #[test]
    fn lists_versions_in_numeric_order() {
        assert_eq!(parse_metadata(METADATA), ["0.10.0", "0.3.2", "0.3.1"]);
        let source = fake(false);
        let releases = Releases::new(&source, None, None);
        assert_eq!(releases.versions(false).unwrap(), ["0.3.1", "0.3.2", "0.10.0"]);
    }

    #[test]
    fn keeps_what_it_fetched_and_uses_it_offline() {
        let cache = tempfile::tempdir().unwrap();
        let online = fake(false);
        let releases = Releases::new(&online, Some(cache.path().to_owned()), None);
        releases.versions(false).unwrap();
        releases.versions(false).unwrap();
        assert_eq!(*online.requests.borrow(), 1, "the second read comes from the cache");
        releases.versions(true).unwrap();
        assert_eq!(*online.requests.borrow(), 2, "a refresh asks again");

        let offline = fake(true);
        let releases = Releases::new(&offline, Some(cache.path().to_owned()), None);
        assert_eq!(releases.versions(true).unwrap().len(), 3);
        assert!(Releases::new(&offline, None, None).versions(false).is_err());
    }

    #[test]
    fn reads_a_catalog_from_gradle_before_asking() {
        let gradle = tempfile::tempdir().unwrap();
        let dir = gradle.path().join("caches/modules-2/files-2.1/tech.annexflow.lvm/lvm-versions/0.3.2/abc123");
        fs::create_dir_all(&dir).unwrap();
        fs::write(dir.join("lvm-versions-0.3.2.toml"), "[versions]\nkotlin = \"2.4.20\"\n").unwrap();
        let source = fake(false);

        let from_gradle = Releases::new(&source, None, Some(gradle.path().to_owned())).catalog("0.3.2").unwrap();
        assert_eq!(from_gradle.get("kotlin").map(String::as_str), Some("2.4.20"));
        assert_eq!(*source.requests.borrow(), 0);

        let fetched = Releases::new(&source, None, None).catalog("0.3.2").unwrap();
        assert_eq!(fetched.get("agp").map(String::as_str), Some("9.5.1"));
    }
}
