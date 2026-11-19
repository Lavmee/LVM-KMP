//! Version catalogs: the build's own, such as `gradle/libs.versions.toml`, and the plugin aliases of `lvm-versions`.

use std::collections::BTreeMap;
use std::path::Path;

use toml_edit::{DocumentMut, Item};

/// LVM's project plugins by the alias that `lvm-versions` gives them after `plugins.`.
pub const BLUEPRINT_ALIASES: &[(&str, &str)] = &[
    ("blueprint.multiplatform", "tech.annexflow.lvm.multiplatform"),
    ("blueprint.jvm", "tech.annexflow.lvm.jvm"),
    ("blueprint.compose", "tech.annexflow.lvm.compose"),
    ("blueprint.quality", "tech.annexflow.lvm.quality"),
    ("blueprint.publish", "tech.annexflow.lvm.publish"),
    ("blueprint.main.context", "tech.annexflow.lvm.main-context"),
    ("blueprint.android.application", "tech.annexflow.lvm.android.application"),
    ("blueprint.android.library", "tech.annexflow.lvm.android.library"),
    ("blueprint.android.test", "tech.annexflow.lvm.android.test"),
];

/// External plugins by their alias in `lvm-versions`, with their IDs.
const EXTERNAL_PLUGINS: &[(&str, &str)] = &[
    ("kotlin.multiplatform", "org.jetbrains.kotlin.multiplatform"),
    ("kotlin.jvm", "org.jetbrains.kotlin.jvm"),
    ("kotlin.serialization", "org.jetbrains.kotlin.plugin.serialization"),
    ("android.application", "com.android.application"),
    ("android.library", "com.android.library"),
    ("android.kmp.library", "com.android.kotlin.multiplatform.library"),
    ("android.test", "com.android.test"),
    ("kotlin.compose", "org.jetbrains.kotlin.plugin.compose"),
    ("compose.multiplatform", "org.jetbrains.compose"),
    ("detekt", "io.gitlab.arturbosch.detekt"),
    ("vanniktech.mavenPublish", "com.vanniktech.maven.publish"),
];

/// The ID of the plugin that `lvm-versions` names [alias].
pub fn alias_to_id(alias: &str) -> Option<&'static str> {
    EXTERNAL_PLUGINS.iter().chain(BLUEPRINT_ALIASES).find(|(name, _)| *name == alias).map(|(_, id)| *id)
}

/// The alias that `lvm-versions` gives the plugin [id].
pub fn id_to_alias(id: &str) -> Option<String> {
    EXTERNAL_PLUGINS
        .iter()
        .chain(BLUEPRINT_ALIASES)
        .find(|(_, plugin)| *plugin == id)
        .map(|(alias, _)| (*alias).to_owned())
}

/// The plugin IDs of the build's own version catalogs, by the catalog's name and the plugin's accessor, as [plugins]
/// reads them.
pub type Plugins = BTreeMap<String, BTreeMap<String, String>>;

/// The ID of the plugin that `alias(<catalog>.plugins.<alias>)` names: the entry of the build's own [catalog], else the
/// alias of `lvm-versions`, whose catalog the settings plugin creates without a file in `gradle`.
pub fn plugin_id<'a>(plugins: &'a Plugins, catalog: &str, alias: &str) -> Option<&'a str> {
    plugins.get(catalog).and_then(|entries| entries.get(alias)).map(String::as_str).or_else(|| alias_to_id(alias))
}

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
pub fn plugins(root: &Path) -> Plugins {
    let Ok(entries) = std::fs::read_dir(root.join("gradle")) else {
        return Plugins::new();
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

    #[test]
    fn takes_the_build_s_own_entry_before_the_alias_of_lvm_versions() {
        let plugins = Plugins::from([(
            "libs".to_owned(),
            BTreeMap::from([
                ("lvm.jvm".to_owned(), "tech.annexflow.lvm.jvm".to_owned()),
                ("blueprint.quality".to_owned(), "com.example.quality".to_owned()),
            ]),
        )]);

        assert_eq!(plugin_id(&plugins, "libs", "lvm.jvm"), Some("tech.annexflow.lvm.jvm"));
        assert_eq!(plugin_id(&plugins, "libs", "blueprint.quality"), Some("com.example.quality"));
        assert_eq!(plugin_id(&plugins, "libs", "blueprint.compose"), Some("tech.annexflow.lvm.compose"));
        assert_eq!(plugin_id(&plugins, "lvmLibs", "lvm.jvm"), None);
    }
}
