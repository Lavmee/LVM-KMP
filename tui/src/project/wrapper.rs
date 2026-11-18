//! The Gradle version of a build, from its wrapper.

use std::path::Path;

use super::properties::Properties;

/// The Gradle version that `gradle/wrapper/gradle-wrapper.properties` of [root] downloads.
pub fn gradle_version(root: &Path) -> Option<String> {
    let properties = Properties::read(&root.join("gradle/wrapper/gradle-wrapper.properties"))?;
    version_from_url(properties.get("distributionUrl")?)
}

/// `https://services.gradle.org/distributions/gradle-9.9.1-bin.zip` -> `9.9.1`.
pub fn version_from_url(url: &str) -> Option<String> {
    let file = url.rsplit('/').next()?;
    let rest = file.strip_prefix("gradle-")?;
    let (version, _) = rest.split_once("-bin").or_else(|| rest.split_once("-all"))?;
    Some(version.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_version_of_bin_and_all_distributions() {
        assert_eq!(
            version_from_url("https://services.gradle.org/distributions/gradle-9.9.1-bin.zip").as_deref(),
            Some("9.9.1")
        );
        assert_eq!(version_from_url("https://example.com/gradle-9.6-rc-1-all.zip").as_deref(), Some("9.6-rc-1"));
        assert_eq!(version_from_url("https://example.com/custom.zip"), None);
    }
}
