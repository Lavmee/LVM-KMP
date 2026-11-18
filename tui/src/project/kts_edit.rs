//! Edits to Kotlin DSL scripts: setting a value in nested blocks such as `lvm { android { } }`, creating the blocks
//! that are missing, and removing statements. Each function returns text edits, so callers can show a diff first.

use std::ops::Range;

use serde_json::Value;

use super::kts::{Form, Kind, Script, Statement};
use crate::edit::TextEdit;

const INDENT: &str = "    ";

/// Sets `name = code` in the block at [blocks], such as `["lvm", "android"]`: replaces the value of an existing
/// assignment, or adds one, with the blocks that don't exist yet.
pub fn set_value(source: &str, blocks: &[&str], name: &str, code: &str) -> Vec<TextEdit> {
    let script = Script::parse(source);
    let (found, body) = find_blocks(&script, blocks);
    if found < blocks.len() {
        let statement = Nested { blocks: &blocks[found..], statement: format!("{name} = {code}") };
        return vec![insert(&script, body, &statement)];
    }
    // Without blocks, the statement goes to the top level of the script.
    let within = body.clone().unwrap_or_else(|| script.all());
    let existing = script.statements(within).into_iter().rev().find_map(|statement| match statement.form {
        Form::Assign { value } if statement.chain == [name] => Some(value),
        _ => None,
    });
    match existing {
        Some(value) => vec![TextEdit::replace(script.byte_span(&value), code)],
        None => vec![insert(&script, body, &Nested { blocks: &[], statement: format!("{name} = {code}") })],
    }
}

/// Removes the statements in the block at [blocks] that write [name]: `name = ...`, `name.add(...)` and the like.
pub fn remove_value(source: &str, blocks: &[&str], name: &str) -> Vec<TextEdit> {
    let script = Script::parse(source);
    let (found, Some(body)) = find_blocks(&script, blocks) else { return Vec::new() };
    if found < blocks.len() {
        return Vec::new();
    }
    script
        .statements(body)
        .into_iter()
        .filter(|statement| statement.chain.first().is_some_and(|first| first == name))
        .filter(|statement| matches!(statement.form, Form::Assign { .. } | Form::Call { .. }))
        .map(|statement| remove_statement(&script, &statement))
        .collect()
}

/// Like [set_value] for a list, which also drops the `add`, `addAll` and `set` calls that would change it again.
pub fn set_list(source: &str, blocks: &[&str], name: &str, code: &str) -> Vec<TextEdit> {
    let script = Script::parse(source);
    let calls: Vec<TextEdit> = match find_blocks(&script, blocks) {
        (found, Some(body)) if found == blocks.len() => script
            .statements(body)
            .into_iter()
            .filter(|s| s.chain.len() == 2 && s.chain[0] == name && matches!(s.form, Form::Call { .. }))
            .map(|s| remove_statement(&script, &s))
            .collect(),
        _ => Vec::new(),
    };
    if calls.is_empty() {
        return set_value(source, blocks, name, code);
    }
    // Apply the removals first, so the assignment is placed in the text that remains.
    let without_calls = crate::edit::apply_edits(source, &calls);
    vec![TextEdit::replace(
        0..source.len(),
        crate::edit::apply_edits(&without_calls, &set_value(&without_calls, blocks, name, code)),
    )]
}

/// Adds [statement] at the end of the block at [blocks], creating the blocks that are missing.
pub fn add_statement(source: &str, blocks: &[&str], statement: &str) -> TextEdit {
    let script = Script::parse(source);
    let (found, body) = find_blocks(&script, blocks);
    insert(&script, body, &Nested { blocks: &blocks[found..], statement: statement.to_owned() })
}

/// A value as Kotlin code: a string literal, a number, a boolean or `listOf(...)`.
pub fn kotlin_literal(value: &Value) -> String {
    match value {
        Value::String(text) => {
            let escaped: String = text
                .chars()
                .flat_map(|c| match c {
                    '"' => vec!['\\', '"'],
                    '\\' => vec!['\\', '\\'],
                    '$' => vec!['\\', '$'],
                    '\n' => vec!['\\', 'n'],
                    c => vec![c],
                })
                .collect();
            format!("\"{escaped}\"")
        }
        Value::Array(items) => {
            let items: Vec<String> = items.iter().map(kotlin_literal).collect();
            if items.is_empty() { "emptyList()".to_owned() } else { format!("listOf({})", items.join(", ")) }
        }
        Value::Null => "null".to_owned(),
        other => other.to_string(),
    }
}

/// Walks down [blocks]; returns how many it found and the body of the deepest one, or `None` for the script itself.
fn find_blocks(script: &Script, blocks: &[&str]) -> (usize, Option<Range<usize>>) {
    let mut body: Option<Range<usize>> = None;
    for (index, name) in blocks.iter().enumerate() {
        let within = body.clone().unwrap_or_else(|| script.all());
        let next = script.statements(within).into_iter().find_map(|statement| match statement.form {
            Form::Block { body } if statement.chain == [*name] => Some(body),
            Form::Call { lambda: Some(body), .. } if statement.chain == [*name] => Some(body),
            _ => None,
        });
        match next {
            Some(next) => body = Some(next),
            None => return (index, body),
        }
    }
    (blocks.len(), body)
}

/// A statement, wrapped in the blocks that don't exist yet.
struct Nested<'a> {
    blocks: &'a [&'a str],
    statement: String,
}

impl Nested<'_> {
    fn lines(&self) -> Vec<String> {
        let depth = self.blocks.len();
        let mut lines: Vec<String> =
            self.blocks.iter().enumerate().map(|(d, block)| format!("{}{block} {{", INDENT.repeat(d))).collect();
        lines.push(format!("{}{}", INDENT.repeat(depth), self.statement));
        lines.extend((0..depth).rev().map(|d| format!("{}}}", INDENT.repeat(d))));
        lines
    }

    fn inline(&self) -> String {
        self.blocks.iter().rev().fold(self.statement.clone(), |inner, block| format!("{block} {{ {inner} }}"))
    }
}

/// Inserts [nested] at the end of [body], or at the end of the script when there is no body.
fn insert(script: &Script, body: Option<Range<usize>>, nested: &Nested) -> TextEdit {
    let source = &script.source;
    let Some(body) = body else {
        let separator = if source.is_empty() || source.ends_with("\n\n") {
            ""
        } else if source.ends_with('\n') {
            "\n"
        } else {
            "\n\n"
        };
        return TextEdit::insert(source.len(), format!("{separator}{}\n", nested.lines().join("\n")));
    };
    let open_end = script.tokens[body.start - 1].span.end;
    let close_start = script.tokens[body.end].span.start;
    let inside = &source[open_end..close_start];
    if !inside.contains('\n') {
        // A block on one line stays on one line: `android { minSdk = 26; compileSdk = 37 }`.
        return if inside.trim().is_empty() {
            TextEdit::replace(open_end..close_start, format!(" {} ", nested.inline()))
        } else {
            let end = open_end + inside.trim_end().len();
            TextEdit::insert(end, format!("; {}", nested.inline()))
        };
    }
    let line_start = source[..close_start].rfind('\n').map_or(0, |i| i + 1);
    let before_brace = &source[line_start..close_start];
    let closing_indent: String = before_brace.chars().take_while(|c| c.is_whitespace()).collect();
    let indent = statement_indent(script, &body).unwrap_or_else(|| format!("{closing_indent}{INDENT}"));
    let text: String = nested.lines().iter().map(|line| format!("{indent}{line}\n")).collect();
    if before_brace.trim().is_empty() {
        TextEdit::insert(line_start, text)
    } else {
        TextEdit::insert(close_start, format!("\n{text}{closing_indent}"))
    }
}

/// The indentation of the first statement of [body] that starts a line.
fn statement_indent(script: &Script, body: &Range<usize>) -> Option<String> {
    let source = &script.source;
    body.clone().find_map(|i| {
        let token = &script.tokens[i];
        if matches!(token.kind, Kind::Newline) {
            return None;
        }
        let line_start = source[..token.span.start].rfind('\n').map_or(0, |n| n + 1);
        let prefix = &source[line_start..token.span.start];
        prefix.chars().all(char::is_whitespace).then(|| prefix.to_owned())
    })
}

/// Removes a statement with its line when it stands alone, or with its `;` when it shares the line.
fn remove_statement(script: &Script, statement: &Statement) -> TextEdit {
    let source = &script.source;
    let span = script.byte_span(&statement.tokens);
    let line_start = source[..span.start].rfind('\n').map_or(0, |i| i + 1);
    let line_end = source[span.end..].find('\n').map_or(source.len(), |i| span.end + i + 1);
    let before = &source[line_start..span.start];
    let after = &source[span.end..line_end];
    if before.trim().is_empty() && after.trim().is_empty() {
        return TextEdit::replace(line_start..line_end, "");
    }
    let after_trimmed = after.trim_start();
    if let Some(rest) = after_trimmed.strip_prefix(';') {
        let skip = after.len() - rest.trim_start().len();
        return TextEdit::replace(span.start..span.end + skip, "");
    }
    // The last statement of a line such as `a = 1; b = 2`: take the `;` before it.
    let semicolon = before.rfind(';').map(|i| line_start + i);
    TextEdit::replace(semicolon.unwrap_or(span.start)..span.end, "")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::edit::apply_edits;

    fn set(source: &str, blocks: &[&str], name: &str, code: &str) -> String {
        apply_edits(source, &set_value(source, blocks, name, code))
    }

    #[test]
    fn replaces_an_existing_value() {
        let source = "lvm {\n    android { minSdk = 24 }\n}\n";
        assert_eq!(set(source, &["lvm", "android"], "minSdk", "26"), "lvm {\n    android { minSdk = 26 }\n}\n");
    }

    #[test]
    fn adds_to_a_block_with_the_indentation_of_its_statements() {
        let source = "lvm {\n  android {\n    minSdk = 24\n  }\n}\n";
        assert_eq!(
            set(source, &["lvm", "android"], "compileSdk", "36"),
            "lvm {\n  android {\n    minSdk = 24\n    compileSdk = 36\n  }\n}\n"
        );
    }

    #[test]
    fn creates_missing_blocks_inside_the_deepest_existing_one() {
        let source = "plugins {\n    id(\"x\")\n}\n\nlvm {\n    catalog = false\n}\n";
        assert_eq!(
            set(source, &["lvm", "kotlin"], "flatLayout", "true"),
            "plugins {\n    id(\"x\")\n}\n\nlvm {\n    catalog = false\n    kotlin {\n        flatLayout = true\n    }\n}\n"
        );
    }

    #[test]
    fn creates_every_block_at_the_end_of_a_script_without_them() {
        let source = "plugins {\n    id(\"x\")\n}\n";
        assert_eq!(
            set(source, &["lvm", "publish", "license"], "name", "\"MIT\""),
            "plugins {\n    id(\"x\")\n}\n\nlvm {\n    publish {\n        license {\n            name = \"MIT\"\n        }\n    }\n}\n"
        );
    }

    #[test]
    fn sets_a_top_level_value() {
        assert_eq!(set("plugins { }\n", &[], "group", "\"a\""), "plugins { }\n\ngroup = \"a\"\n");
        assert_eq!(set("group = \"a\"\n", &[], "group", "\"b\""), "group = \"b\"\n");
    }

    #[test]
    fn keeps_one_line_blocks_on_one_line() {
        assert_eq!(
            set("lvm { android { minSdk = 26 } }\n", &["lvm", "android"], "compileSdk", "36"),
            "lvm { android { minSdk = 26; compileSdk = 36 } }\n"
        );
        assert_eq!(set("lvm { }\n", &["lvm", "jvm"], "target", "17"), "lvm { jvm { target = 17 } }\n");
    }

    #[test]
    fn a_list_replaces_the_calls_that_build_it() {
        let source = "lvm {\n    kotlin {\n        optIns.add(\"a\")\n        optIns.addAll(\"b\", \"c\")\n        explicitApi = true\n    }\n}\n";
        let edited = apply_edits(source, &set_list(source, &["lvm", "kotlin"], "optIns", "listOf(\"x\")"));
        assert_eq!(
            edited,
            "lvm {\n    kotlin {\n        explicitApi = true\n        optIns = listOf(\"x\")\n    }\n}\n"
        );
    }

    #[test]
    fn removes_statements_alone_or_sharing_a_line() {
        let source =
            "lvm {\n    android { minSdk = 26; compileSdk = 36 }\n    kotlin {\n        optIns.add(\"a\")\n    }\n}\n";
        let without_min = apply_edits(source, &remove_value(source, &["lvm", "android"], "minSdk"));
        assert_eq!(
            without_min,
            "lvm {\n    android { compileSdk = 36 }\n    kotlin {\n        optIns.add(\"a\")\n    }\n}\n"
        );
        let without_compile = apply_edits(source, &remove_value(source, &["lvm", "android"], "compileSdk"));
        assert_eq!(
            without_compile,
            "lvm {\n    android { minSdk = 26 }\n    kotlin {\n        optIns.add(\"a\")\n    }\n}\n"
        );
        let without_opt_ins = apply_edits(source, &remove_value(source, &["lvm", "kotlin"], "optIns"));
        assert_eq!(without_opt_ins, "lvm {\n    android { minSdk = 26; compileSdk = 36 }\n    kotlin {\n    }\n}\n");
    }

    #[test]
    fn writes_values_as_kotlin() {
        assert_eq!(kotlin_literal(&"a\"$b".into()), "\"a\\\"\\$b\"");
        assert_eq!(kotlin_literal(&serde_json::json!(["a", "b"])), "listOf(\"a\", \"b\")");
        assert_eq!(kotlin_literal(&serde_json::json!([])), "emptyList()");
        assert_eq!(kotlin_literal(&26.into()), "26");
        assert_eq!(kotlin_literal(&true.into()), "true");
    }
}
