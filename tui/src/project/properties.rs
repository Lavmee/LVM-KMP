//! Java `.properties` files, as Gradle reads `gradle.properties` and `gradle-wrapper.properties`.

use std::fs;
use std::path::Path;

use serde::Serialize;

/// One entry, with the 1-based line it starts on.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Property {
    pub key: String,
    pub value: String,
    pub line: usize,
}

/// The entries of a `.properties` file in their order. A key that repeats keeps its last value, as in Java.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
pub struct Properties {
    pub entries: Vec<Property>,
}

impl Properties {
    pub fn read(path: &Path) -> Option<Self> {
        fs::read_to_string(path).ok().map(|text| Self::parse(&text))
    }

    pub fn parse(text: &str) -> Self {
        let mut entries = Vec::new();
        let mut lines = text.lines().enumerate().peekable();
        while let Some((index, line)) = lines.next() {
            let start = line.trim_start();
            if start.is_empty() || start.starts_with('#') || start.starts_with('!') {
                continue;
            }
            // A line that ends in an odd number of backslashes continues on the next one.
            let mut logical = start.to_owned();
            while ends_with_continuation(&logical) {
                logical.pop();
                match lines.next() {
                    Some((_, next)) => logical.push_str(next.trim_start()),
                    None => break,
                }
            }
            let (key, value) = split_entry(&logical);
            entries.push(Property { key: unescape(&key), value: unescape(&value), line: index + 1 });
        }
        Self { entries }
    }

    pub fn get(&self, key: &str) -> Option<&str> {
        self.entries.iter().rev().find(|entry| entry.key == key).map(|entry| entry.value.as_str())
    }

    pub fn entry(&self, key: &str) -> Option<&Property> {
        self.entries.iter().rev().find(|entry| entry.key == key)
    }
}

fn ends_with_continuation(line: &str) -> bool {
    line.chars().rev().take_while(|&c| c == '\\').count() % 2 == 1
}

/// Splits at the first unescaped `=`, `:` or whitespace, as `java.util.Properties` does.
fn split_entry(line: &str) -> (String, String) {
    let mut key = String::new();
    let mut chars = line.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\\' => {
                key.push(c);
                if let Some(next) = chars.next() {
                    key.push(next);
                }
            }
            '=' | ':' => break,
            c if c.is_whitespace() => {
                // Whitespace, then optionally one `=` or `:`, separates the key from the value.
                while chars.peek().is_some_and(|c| c.is_whitespace()) {
                    chars.next();
                }
                if matches!(chars.peek(), Some('=' | ':')) {
                    chars.next();
                }
                break;
            }
            _ => key.push(c),
        }
    }
    let value: String = chars.collect();
    (key, value.trim_start().to_owned())
}

fn unescape(text: &str) -> String {
    let mut result = String::with_capacity(text.len());
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        if c != '\\' {
            result.push(c);
            continue;
        }
        match chars.next() {
            Some('t') => result.push('\t'),
            Some('n') => result.push('\n'),
            Some('r') => result.push('\r'),
            Some('f') => result.push('\u{c}'),
            Some('u') => {
                let hex: String = chars.by_ref().take(4).collect();
                match u32::from_str_radix(&hex, 16).ok().and_then(char::from_u32) {
                    Some(decoded) => result.push(decoded),
                    None => result.push_str(&hex),
                }
            }
            Some(other) => result.push(other),
            None => {}
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_separators_comments_and_line_numbers() {
        let properties = Properties::parse("# comment\n! also a comment\na=1\nb : 2\nc 3\n\n  d=  spaced  \n");
        let keys: Vec<_> = properties.entries.iter().map(|e| (e.key.as_str(), e.value.as_str(), e.line)).collect();
        assert_eq!(keys, vec![("a", "1", 3), ("b", "2", 4), ("c", "3", 5), ("d", "spaced  ", 7)]);
    }

    #[test]
    fn joins_continued_lines_and_unescapes() {
        let properties = Properties::parse("list=a,\\\n    b,\\\n    c\nurl=https\\://example.com\nescaped\\ key=x\n");
        assert_eq!(properties.get("list"), Some("a,b,c"));
        assert_eq!(properties.get("url"), Some("https://example.com"));
        assert_eq!(properties.get("escaped key"), Some("x"));
    }

    #[test]
    fn a_repeated_key_keeps_its_last_value() {
        let properties = Properties::parse("a=1\na=2\n");
        assert_eq!(properties.get("a"), Some("2"));
        assert_eq!(properties.entry("a").map(|e| e.line), Some(2));
    }

    #[test]
    fn an_empty_value_is_kept() {
        let properties = Properties::parse("lvm.application.proguardFiles=\n");
        assert_eq!(properties.get("lvm.application.proguardFiles"), Some(""));
    }
}
