//! The build's own version catalog, `gradle/libs.versions.toml`.

use std::collections::BTreeMap;
use std::path::Path;

use toml_edit::DocumentMut;

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
}
