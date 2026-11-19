//! The build's own version catalogs, such as `gradle/libs.versions.toml`.

use std::collections::BTreeMap;
use std::path::Path;

use toml_edit::{DocumentMut, Item};

/// The `[versions]` of `gradle/libs.versions.toml` in [root], where they are plain strings.
pub fn versions(root: &Path) -> BTreeMap<String, String> {
    std::fs::read_to_string(root.join("gradle/libs.versions.toml"))
        .ok()
        .map(|text| parse_versions(&text))
        .unwrap_or_default()
}

pub fn parse_versions(text: &str) -> BTreeMap<String, String> {
    let Ok(document) = text.parse::<DocumentMut>() else {
        return BTreeMap::new();
    };
    let Some(table) = document.get("versions").and_then(|item| item.as_table_like()) else {
        return BTreeMap::new();
    };
    table.iter().filter_map(|(name, item)| item.as_str().map(|version| (name.to_owned(), version.to_owned()))).collect()
}

/// The plugin IDs in the `[plugins]` of each version catalog in the `gradle` directory of [root], by the catalog's
/// name and the plugin's accessor. A catalog is named after its file, as `libs` for `gradle/libs.versions.toml`.
pub fn plugins(root: &Path) -> BTreeMap<String, BTreeMap<String, String>> {
    let Ok(entries) = std::fs::read_dir(root.join("gradle")) else {
        return BTreeMap::new();
    };
    entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().to_str()?.strip_suffix(".versions.toml")?.to_owned();
            let text = std::fs::read_to_string(entry.path()).ok()?;
            Some((name, parse_plugins(&text)))
        })
        .collect()
}

/// The plugin IDs in `[plugins]` by the accessor after `plugins.`, in which Gradle turns `-` and `_` of the key into
/// `.`: `lvm-android_library` is `lvm.android.library`.
pub fn parse_plugins(text: &str) -> BTreeMap<String, String> {
    let Ok(document) = text.parse::<DocumentMut>() else {
        return BTreeMap::new();
    };
    let Some(table) = document.get("plugins").and_then(|item| item.as_table_like()) else {
        return BTreeMap::new();
    };
    table
        .iter()
        .filter_map(|(key, item)| {
            let id = match item.as_str() {
                // "org.jetbrains.kotlin.jvm:2.4.21"
                Some(notation) => notation.split(':').next(),
                None => item.as_table_like().and_then(|entry| entry.get("id")).and_then(Item::as_str),
            }?;
            Some((key.replace(['-', '_'], "."), id.to_owned()))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_plain_versions_and_skips_rich_ones() {
        let versions =
            parse_versions("[versions]\nkotlin = \"2.4.21\"\nagp = \"9.5.1\"\nrich = { strictly = \"1.0\" }\n");
        assert_eq!(versions.get("kotlin").map(String::as_str), Some("2.4.21"));
        assert_eq!(versions.get("agp").map(String::as_str), Some("9.5.1"));
        assert!(!versions.contains_key("rich"));
    }

    #[test]
    fn reads_plugin_ids_by_the_accessor_that_gradle_generates() {
        let plugins = parse_plugins(
            "[plugins]\nlvm-android_library = { id = \"tech.annexflow.lvm.android.library\", version.ref = \"lvm\" }\n\"lvm.compose\" = { id = \"tech.annexflow.lvm.compose\", version = \"0.5.0\" }\nkotlin-jvm = \"org.jetbrains.kotlin.jvm:2.4.21\"\nbroken = { version = \"1.0\" }\n",
        );
        assert_eq!(
            plugins,
            BTreeMap::from([
                ("lvm.android.library".to_owned(), "tech.annexflow.lvm.android.library".to_owned()),
                ("lvm.compose".to_owned(), "tech.annexflow.lvm.compose".to_owned()),
                ("kotlin.jvm".to_owned(), "org.jetbrains.kotlin.jvm".to_owned()),
            ])
        );
    }
}
