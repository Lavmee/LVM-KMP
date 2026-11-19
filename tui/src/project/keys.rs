//! LVM's build-wide keys, from `blueprint/common/lvm-keys.json`, which blueprint's tests keep in step with its code.

use std::sync::OnceLock;

use serde::{Deserialize, Serialize};
use serde_json::Value;

// build.rs copies blueprint/common/lvm-keys.json there.
const KEYS_JSON: &str = include_str!(concat!(env!("OUT_DIR"), "/lvm-keys.json"));

#[derive(Debug, Clone, Deserialize)]
pub struct KeysFile {
    pub minimums: Minimums,
    pub keys: Vec<Key>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Minimums {
    pub gradle: String,
    pub kotlin: String,
    pub agp: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Key {
    pub key: String,
    #[serde(rename = "type")]
    pub kind: KeyType,
    pub default: Option<Value>,
    /// How a module's list combines with the build-wide one.
    pub module: Option<ListMode>,
    #[serde(default)]
    pub build_wide_only: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum KeyType {
    Int,
    Boolean,
    String,
    List,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum ListMode {
    Append,
    Replace,
}

/// The keys of the LVM release that lvm-tui ships with.
pub fn keys() -> &'static KeysFile {
    static KEYS: OnceLock<KeysFile> = OnceLock::new();
    KEYS.get_or_init(|| serde_json::from_str(KEYS_JSON).expect("lvm-keys.json is valid"))
}

pub fn find(key: &str) -> Option<&'static Key> {
    keys().keys.iter().find(|k| k.key == key)
}

impl Key {
    /// The section of `lvm { }` the key belongs to, such as `android` for `android.minSdk`.
    pub fn section(&self) -> &str {
        self.key.split('.').next().unwrap_or_default()
    }

    /// Parses the string form of the value, as a Gradle property or a forwarded settings value carries it, the way
    /// blueprint does: an integer, `true` or `false` in any case, or a comma-separated list without blank entries.
    pub fn parse(&self, raw: &str) -> Result<Value, String> {
        let property = format!("lvm.{}", self.key);
        match self.kind {
            KeyType::Int => raw
                .trim()
                .parse::<i64>()
                .map(Value::from)
                .map_err(|_| format!("{property} must be an integer, but was '{raw}'")),
            KeyType::Boolean => match raw.trim().to_lowercase().as_str() {
                "true" => Ok(Value::Bool(true)),
                "false" => Ok(Value::Bool(false)),
                _ => Err(format!("{property} must be true or false, but was '{raw}'")),
            },
            KeyType::String => Ok(Value::String(raw.to_owned())),
            KeyType::List => Ok(parse_list(raw)),
        }
    }

    /// Converts a value that a script writes, such as `"26"` from `values.put(...)`, to the key's type.
    pub fn coerce(&self, value: Value) -> Value {
        match (&value, self.kind) {
            (Value::String(raw), KeyType::Int | KeyType::Boolean | KeyType::List) => self.parse(raw).unwrap_or(value),
            _ => value,
        }
    }
}

/// A comma-separated list without blank entries, as blueprint reads a list from a Gradle property.
pub fn parse_list(raw: &str) -> Value {
    Value::Array(raw.split(',').map(str::trim).filter(|s| !s.is_empty()).map(|s| Value::String(s.to_owned())).collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_key_has_a_type_and_lists_have_a_mode() {
        assert!(!keys().keys.is_empty());
        for key in &keys().keys {
            assert_eq!(key.kind == KeyType::List, key.module.is_some(), "{}", key.key);
        }
    }

    #[test]
    fn parses_values_as_blueprint_does() {
        let min_sdk = find("android.minSdk").unwrap();
        assert_eq!(min_sdk.parse(" 26 "), Ok(26.into()));
        assert!(min_sdk.parse("abc").is_err());
        assert_eq!(find("kotlin.flatLayout").unwrap().parse("TRUE"), Ok(true.into()));
        assert_eq!(find("kotlin.optIns").unwrap().parse("a, ,b,"), Ok(serde_json::json!(["a", "b"])));
        assert_eq!(find("application.proguardFiles").unwrap().parse(""), Ok(serde_json::json!([])));
    }
}
