//! A scanner for Gradle build scripts in the Kotlin DSL.
//!
//! It doesn't parse Kotlin. It splits a script into tokens, skipping comments and keeping string literals whole, and
//! reads the statements of a block: assignments (`minSdk = 26`), calls (`include(":app")`, `id("x") version "1.0"`)
//! and blocks (`lvm { ... }`). That covers how build scripts configure Gradle and LVM; anything else is reported as an
//! unknown statement and skipped. Every token keeps its byte span, so callers can point at a line or edit the text.

use std::ops::Range;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Kind {
    /// An identifier or keyword. A backticked one, such as `` `kotlin-dsl` ``, keeps its backticks in the span.
    Ident,
    /// A string literal, with its value when it has no templates.
    Str(Option<String>),
    Number,
    Open(char),
    Close(char),
    Dot,
    Comma,
    /// `=` alone, not part of `==`, `+=` or another operator.
    Assign,
    Semicolon,
    Newline,
    Other,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Token {
    pub kind: Kind,
    pub span: Range<usize>,
}

#[derive(Debug, Clone)]
pub struct Script {
    pub source: String,
    pub tokens: Vec<Token>,
    /// For each bracket token, the index of the bracket that closes or opens it.
    matching: Vec<Option<usize>>,
}

/// A statement at the top level of a block: `a.b.c` followed by what it does.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Statement {
    /// The dotted name the statement starts with, such as `["rootProject", "name"]`.
    pub chain: Vec<String>,
    pub form: Form,
    /// Tokens of the whole statement.
    pub tokens: Range<usize>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Form {
    /// `chain = value`
    Assign { value: Range<usize> },
    /// `chain(args)`, maybe with a trailing lambda, then whatever follows on the line, such as `version "1.0"`.
    Call { args: Vec<Range<usize>>, lambda: Option<Range<usize>>, rest: Range<usize> },
    /// `chain { body }`
    Block { body: Range<usize> },
    /// Anything else, such as `val x = ...` or `` `kotlin-dsl` ``.
    Other,
}

/// A value that a script writes literally.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Literal {
    Str(String),
    Int(i64),
    Bool(bool),
    List(Vec<String>),
}

impl Script {
    pub fn parse(source: &str) -> Self {
        let tokens = tokenize(source);
        let matching = match_brackets(&tokens);
        Self { source: source.to_owned(), tokens, matching }
    }

    /// All tokens.
    pub fn all(&self) -> Range<usize> {
        0..self.tokens.len()
    }

    /// The text of token [index]; an identifier without its backticks.
    pub fn text(&self, index: usize) -> &str {
        let token = &self.tokens[index];
        let text = &self.source[token.span.clone()];
        if token.kind == Kind::Ident { text.trim_matches('`') } else { text }
    }

    /// The 1-based line of token [index].
    pub fn line(&self, index: usize) -> usize {
        let offset = self.tokens.get(index).map_or(self.source.len(), |t| t.span.start);
        self.source[..offset].matches('\n').count() + 1
    }

    /// The bytes that [tokens] cover.
    pub fn byte_span(&self, tokens: &Range<usize>) -> Range<usize> {
        if tokens.is_empty() {
            let at = self.tokens.get(tokens.start).map_or(self.source.len(), |t| t.span.start);
            return at..at;
        }
        self.tokens[tokens.start].span.start..self.tokens[tokens.end - 1].span.end
    }

    /// The statements at the top level of [within], usually the body of a block.
    pub fn statements(&self, within: Range<usize>) -> Vec<Statement> {
        let mut statements = Vec::new();
        let mut i = within.start;
        while i < within.end {
            if matches!(self.tokens[i].kind, Kind::Newline | Kind::Semicolon) {
                i += 1;
                continue;
            }
            let start = i;
            let (statement, end) = self.statement(i, within.end);
            statements.push(Statement { tokens: start..end, ..statement });
            i = end.max(start + 1);
        }
        statements
    }

    /// The statements of every block named [name] at the top level of [within].
    pub fn block_statements(&self, within: Range<usize>, name: &str) -> Vec<Statement> {
        self.statements(within)
            .into_iter()
            .filter(|s| s.chain == [name])
            .filter_map(|s| match s.form {
                Form::Block { body } => Some(body),
                Form::Call { lambda: Some(body), .. } => Some(body),
                _ => None,
            })
            .flat_map(|body| self.statements(body))
            .collect()
    }

    /// Every call of [name] anywhere in [within], whatever its depth, as the tokens of its arguments.
    pub fn calls(&self, within: Range<usize>, name: &str) -> Vec<(usize, Vec<Range<usize>>)> {
        let mut calls = Vec::new();
        for i in within.clone() {
            if self.tokens[i].kind != Kind::Ident || self.text(i) != name {
                continue;
            }
            let Some(open) = self.tokens.get(i + 1).filter(|t| t.kind == Kind::Open('(')).map(|_| i + 1) else {
                continue;
            };
            if i > within.start && self.tokens[i - 1].kind == Kind::Dot {
                continue;
            }
            if let Some(close) = self.matching[open].filter(|&close| close < within.end) {
                calls.push((i, self.split_args(open + 1..close)));
            }
        }
        calls
    }

    /// The dotted name that starts at token [index], such as `lvmLibs.plugins.blueprint.multiplatform`.
    pub fn chain_at(&self, index: usize) -> Vec<String> {
        let mut chain = Vec::new();
        let mut i = index;
        while i < self.tokens.len() && self.tokens[i].kind == Kind::Ident {
            chain.push(self.text(i).to_owned());
            if self.tokens.get(i + 1).is_some_and(|t| t.kind == Kind::Dot)
                && self.tokens.get(i + 2).is_some_and(|t| t.kind == Kind::Ident)
            {
                i += 2;
            } else {
                break;
            }
        }
        chain
    }

    /// The value of [tokens] when the script writes it literally: a string, an integer, a boolean, or a list of
    /// strings made with `listOf`, `setOf`, `emptyList` and their relatives.
    pub fn literal(&self, tokens: Range<usize>) -> Option<Literal> {
        let significant: Vec<usize> = tokens.filter(|&i| self.tokens[i].kind != Kind::Newline).collect();
        match significant.as_slice() {
            [single] => match &self.tokens[*single].kind {
                Kind::Str(Some(value)) => Some(Literal::Str(value.clone())),
                Kind::Number => self.text(*single).replace('_', "").parse().ok().map(Literal::Int),
                Kind::Ident => match self.text(*single) {
                    "true" => Some(Literal::Bool(true)),
                    "false" => Some(Literal::Bool(false)),
                    _ => None,
                },
                _ => None,
            },
            [name, open, .., close]
                if self.tokens[*name].kind == Kind::Ident
                    && self.tokens[*open].kind == Kind::Open('(')
                    && self.matching[*open] == Some(*close) =>
            {
                if !LIST_FACTORIES.contains(&self.text(*name)) {
                    return None;
                }
                self.string_args(&self.split_args(*open + 1..*close)).map(Literal::List)
            }
            _ => None,
        }
    }

    /// The values of [args] when each is a plain string literal.
    pub fn string_args(&self, args: &[Range<usize>]) -> Option<Vec<String>> {
        args.iter()
            .map(|arg| match self.literal(arg.clone()) {
                Some(Literal::Str(value)) => Some(value),
                _ => None,
            })
            .collect()
    }

    fn statement(&self, start: usize, end: usize) -> (Statement, usize) {
        let other = |chain: Vec<String>, at: usize| {
            let stop = self.statement_end(at, end);
            (Statement { chain, form: Form::Other, tokens: start..stop }, stop)
        };
        if self.tokens[start].kind != Kind::Ident {
            return other(Vec::new(), start);
        }
        let chain = self.chain_at(start);
        let after = start + chain.len() * 2 - 1;
        let Some(next) = self.tokens.get(after).filter(|_| after < end) else {
            return (Statement { chain, form: Form::Other, tokens: start..after.min(end) }, after.min(end));
        };
        match next.kind {
            Kind::Assign => {
                let stop = self.statement_end(after + 1, end);
                (Statement { chain, form: Form::Assign { value: after + 1..stop }, tokens: start..stop }, stop)
            }
            Kind::Open('{') => match self.matching[after].filter(|&close| close < end) {
                Some(close) => {
                    let stop = self.statement_end(close + 1, end);
                    (Statement { chain, form: Form::Block { body: after + 1..close }, tokens: start..stop }, stop)
                }
                None => other(chain, after),
            },
            Kind::Open('(') => match self.matching[after].filter(|&close| close < end) {
                Some(close) => {
                    let args = self.split_args(after + 1..close);
                    let lambda_open = close + 1;
                    let lambda = (self.tokens.get(lambda_open).is_some_and(|t| t.kind == Kind::Open('{'))
                        && lambda_open < end)
                        .then(|| self.matching[lambda_open].filter(|&c| c < end).map(|c| lambda_open + 1..c))
                        .flatten();
                    let rest_start = lambda.as_ref().map_or(close + 1, |body| body.end + 1);
                    let stop = self.statement_end(rest_start, end);
                    let form = Form::Call { args, lambda, rest: rest_start..stop };
                    (Statement { chain, form, tokens: start..stop }, stop)
                }
                None => other(chain, after),
            },
            _ => other(chain, after),
        }
    }

    /// The index after the last token of a statement that continues at [from]: the next newline or semicolon that
    /// isn't inside brackets, or [end].
    fn statement_end(&self, from: usize, end: usize) -> usize {
        let mut i = from;
        while i < end {
            match self.tokens[i].kind {
                Kind::Newline | Kind::Semicolon => return i,
                Kind::Open(_) => i = self.matching[i].map_or(end, |close| close + 1),
                _ => i += 1,
            }
        }
        end
    }

    /// Splits the tokens between brackets at their top-level commas, dropping newlines at either end.
    fn split_args(&self, inside: Range<usize>) -> Vec<Range<usize>> {
        let mut args = Vec::new();
        let mut start = inside.start;
        let mut i = inside.start;
        while i < inside.end {
            match self.tokens[i].kind {
                Kind::Comma => {
                    args.push(self.trim(start..i));
                    start = i + 1;
                    i += 1;
                }
                Kind::Open(_) => i = self.matching[i].map_or(inside.end, |close| close + 1),
                _ => i += 1,
            }
        }
        let last = self.trim(start..inside.end);
        if !last.is_empty() || !args.is_empty() {
            args.push(last);
        }
        args.retain(|arg| !arg.is_empty());
        args
    }

    fn trim(&self, mut range: Range<usize>) -> Range<usize> {
        while range.start < range.end && self.tokens[range.start].kind == Kind::Newline {
            range.start += 1;
        }
        while range.end > range.start && self.tokens[range.end - 1].kind == Kind::Newline {
            range.end -= 1;
        }
        range
    }
}

const LIST_FACTORIES: &[&str] =
    &["listOf", "setOf", "mutableListOf", "mutableSetOf", "emptyList", "emptySet", "arrayOf"];

fn match_brackets(tokens: &[Token]) -> Vec<Option<usize>> {
    let mut matching = vec![None; tokens.len()];
    let mut stack: Vec<(usize, char)> = Vec::new();
    for (i, token) in tokens.iter().enumerate() {
        match token.kind {
            Kind::Open(c) => stack.push((i, c)),
            Kind::Close(c) => {
                let opening = match c {
                    ')' => '(',
                    ']' => '[',
                    _ => '{',
                };
                // A stray bracket leaves the stack as it is, so one typo doesn't unbalance the rest.
                if let Some(position) = stack.iter().rposition(|&(_, open)| open == opening) {
                    let (open, _) = stack[position];
                    stack.truncate(position);
                    matching[open] = Some(i);
                    matching[i] = Some(open);
                }
            }
            _ => {}
        }
    }
    matching
}

fn tokenize(source: &str) -> Vec<Token> {
    let bytes = source.as_bytes();
    let mut tokens = Vec::new();
    let mut i = 0;
    while i < bytes.len() {
        let start = i;
        let c = bytes[i];
        let kind = match c {
            b'\n' => {
                i += 1;
                Kind::Newline
            }
            c if c.is_ascii_whitespace() => {
                i += 1;
                continue;
            }
            b'/' if bytes.get(i + 1) == Some(&b'/') => {
                while i < bytes.len() && bytes[i] != b'\n' {
                    i += 1;
                }
                continue;
            }
            b'/' if bytes.get(i + 1) == Some(&b'*') => {
                i = skip_block_comment(bytes, i);
                continue;
            }
            b'"' if bytes[i..].starts_with(b"\"\"\"") => {
                let (end, templated) = scan_raw_string(bytes, i);
                i = end;
                let content_end = end.saturating_sub(3).max(start + 3);
                Kind::Str((!templated).then(|| source[start + 3..content_end].to_owned()))
            }
            b'"' | b'\'' => {
                let (end, value) = scan_string(source, i, c);
                i = end;
                Kind::Str(value)
            }
            b'`' => {
                i += 1;
                while i < bytes.len() && bytes[i] != b'`' && bytes[i] != b'\n' {
                    i += 1;
                }
                i = (i + 1).min(bytes.len());
                Kind::Ident
            }
            c if c.is_ascii_digit() => {
                while i < bytes.len() && (bytes[i].is_ascii_alphanumeric() || bytes[i] == b'_' || bytes[i] == b'.') {
                    // `1.dp` is a call on a number, not a fraction: stop at a dot that isn't followed by a digit.
                    if bytes[i] == b'.' && !bytes.get(i + 1).is_some_and(u8::is_ascii_digit) {
                        break;
                    }
                    i += 1;
                }
                Kind::Number
            }
            c if c.is_ascii_alphabetic() || c == b'_' || c >= 0x80 => {
                while i < bytes.len() && (bytes[i].is_ascii_alphanumeric() || bytes[i] == b'_' || bytes[i] >= 0x80) {
                    i += 1;
                }
                Kind::Ident
            }
            b'(' | b'{' | b'[' => {
                i += 1;
                Kind::Open(c as char)
            }
            b')' | b'}' | b']' => {
                i += 1;
                Kind::Close(c as char)
            }
            b'.' => {
                i += 1;
                Kind::Dot
            }
            b',' => {
                i += 1;
                Kind::Comma
            }
            b';' => {
                i += 1;
                Kind::Semicolon
            }
            b'=' => {
                i += 1;
                if bytes.get(i) == Some(&b'=') {
                    while bytes.get(i) == Some(&b'=') {
                        i += 1;
                    }
                    Kind::Other
                } else {
                    Kind::Assign
                }
            }
            b'+' | b'-' | b'*' | b'%' | b'!' | b'<' | b'>' | b'/' | b'?' | b':' | b'&' | b'|' => {
                i += 1;
                // Keep compound operators such as `+=`, `!=` or `->` in one token, so `=` stays an assignment.
                if matches!(bytes.get(i), Some(b'=' | b'>' | b'&' | b'|' | b':' | b'.'))
                    && !(c == b'?' && bytes.get(i) == Some(&b'.'))
                {
                    i += 1;
                }
                Kind::Other
            }
            _ => {
                i += source[i..].chars().next().map_or(1, char::len_utf8);
                Kind::Other
            }
        };
        tokens.push(Token { kind, span: start..i });
    }
    tokens
}

fn skip_block_comment(bytes: &[u8], start: usize) -> usize {
    let mut depth = 0;
    let mut i = start;
    while i < bytes.len() {
        if bytes[i..].starts_with(b"/*") {
            depth += 1;
            i += 2;
        } else if bytes[i..].starts_with(b"*/") {
            depth -= 1;
            i += 2;
            if depth == 0 {
                return i;
            }
        } else {
            i += 1;
        }
    }
    bytes.len()
}

/// Scans a raw string that starts at [start]; returns the index after it and whether it has templates.
fn scan_raw_string(bytes: &[u8], start: usize) -> (usize, bool) {
    let mut i = start + 3;
    let mut templated = false;
    while i < bytes.len() {
        if bytes[i..].starts_with(b"\"\"\"") {
            // A raw string ends at the last quote of a run, so `""""` ends with a quote in its content.
            let mut end = i + 3;
            while bytes.get(end) == Some(&b'"') {
                end += 1;
            }
            return (end, templated);
        }
        if bytes[i] == b'$' {
            if bytes.get(i + 1) == Some(&b'{') {
                templated = true;
                i = skip_template(bytes, i + 1);
                continue;
            }
            if bytes.get(i + 1).is_some_and(|c| c.is_ascii_alphabetic() || *c == b'_') {
                templated = true;
            }
        }
        i += 1;
    }
    (bytes.len(), templated)
}

/// Scans a string quoted with [quote] that starts at [start]; returns the index after it and its value when it has no
/// templates. Single quotes make a string too, as in the Groovy DSL.
fn scan_string(source: &str, start: usize, quote: u8) -> (usize, Option<String>) {
    let bytes = source.as_bytes();
    let mut i = start + 1;
    let mut value = String::new();
    let mut templated = false;
    while i < bytes.len() {
        match bytes[i] {
            b'\n' => return (i, None),
            b'\\' => {
                let escaped = bytes.get(i + 1).copied();
                match escaped {
                    Some(b'n') => value.push('\n'),
                    Some(b't') => value.push('\t'),
                    Some(b'r') => value.push('\r'),
                    Some(b'b') => value.push('\u{8}'),
                    Some(b'u') => {
                        let hex = source.get(i + 2..i + 6).unwrap_or("");
                        if let Some(c) = u32::from_str_radix(hex, 16).ok().and_then(char::from_u32) {
                            value.push(c);
                        }
                        i += 4;
                    }
                    Some(other) => value.push(other as char),
                    None => {}
                }
                i += 2;
            }
            b'$' if quote == b'"' && bytes.get(i + 1) == Some(&b'{') => {
                templated = true;
                i = skip_template(bytes, i + 1);
            }
            b'$' if quote == b'"' && bytes.get(i + 1).is_some_and(|c| c.is_ascii_alphabetic() || *c == b'_') => {
                templated = true;
                i += 1;
            }
            c if c == quote => return (i + 1, (!templated).then_some(value)),
            _ => {
                let c = source[i..].chars().next().unwrap_or('\u{fffd}');
                value.push(c);
                i += c.len_utf8();
            }
        }
    }
    (bytes.len(), None)
}

/// Skips the `{ ... }` of a string template that opens at [open], including strings nested in it.
fn skip_template(bytes: &[u8], open: usize) -> usize {
    let mut depth = 0;
    let mut i = open;
    while i < bytes.len() {
        match bytes[i] {
            b'{' => depth += 1,
            b'}' => {
                depth -= 1;
                if depth == 0 {
                    return i + 1;
                }
            }
            b'"' => {
                // A string inside the template; its own braces don't count.
                i += 1;
                while i < bytes.len() && bytes[i] != b'"' && bytes[i] != b'\n' {
                    i += if bytes[i] == b'\\' { 2 } else { 1 };
                }
            }
            _ => {}
        }
        i += 1;
    }
    bytes.len()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn chains(script: &Script, statements: &[Statement]) -> Vec<String> {
        let _ = script;
        statements.iter().map(|s| s.chain.join(".")).collect()
    }

    #[test]
    fn skips_comments_and_keeps_strings_whole() {
        let script = Script::parse("a = \"x // not a comment\" // comment\n/* block /* nested */ still */ b = 'y'\n");
        let statements = script.statements(script.all());
        assert_eq!(chains(&script, &statements), ["a", "b"]);
        let values: Vec<_> = statements
            .iter()
            .map(|s| match &s.form {
                Form::Assign { value } => script.literal(value.clone()),
                _ => None,
            })
            .collect();
        assert_eq!(values, [Some(Literal::Str("x // not a comment".into())), Some(Literal::Str("y".into()))]);
    }

    #[test]
    fn a_string_with_a_template_has_no_value() {
        let script = Script::parse(
            r#"a = "v${version.get().let { "}" }}" + 1; b = "$x"; c = """raw ${'$'}"""; d = """plain""""#,
        );
        let literals: Vec<_> = script
            .statements(script.all())
            .into_iter()
            .map(|s| match s.form {
                Form::Assign { value } => script.literal(value),
                _ => None,
            })
            .collect();
        assert_eq!(literals, [None, None, None, Some(Literal::Str("plain".into()))]);
    }

    #[test]
    fn reads_calls_with_infix_rest_blocks_and_lambdas() {
        let script = Script::parse(
            "plugins {\n    id(\"a\") version \"1.0\" apply false\n    alias(libs.plugins.b)\n}\ntasks.register(\"x\") { }\n",
        );
        let top = script.statements(script.all());
        assert_eq!(chains(&script, &top), ["plugins", "tasks.register"]);
        assert!(matches!(&top[1].form, Form::Call { lambda: Some(_), .. }));

        let plugins = script.block_statements(script.all(), "plugins");
        assert_eq!(chains(&script, &plugins), ["id", "alias"]);
        let Form::Call { args, rest, .. } = &plugins[0].form else { panic!("not a call") };
        assert_eq!(script.string_args(args), Some(vec!["a".to_owned()]));
        let rest: Vec<_> = rest.clone().map(|i| script.text(i)).collect();
        assert_eq!(rest, ["version", "\"1.0\"", "apply", "false"]);
        let Form::Call { args, .. } = &plugins[1].form else { panic!("not a call") };
        assert_eq!(script.chain_at(args[0].start), ["libs", "plugins", "b"]);
    }

    #[test]
    fn statements_end_at_newlines_and_semicolons_outside_brackets() {
        let script = Script::parse(
            "framework { baseName = \"Kit\"; isStatic = true }\nlist = listOf(\n    \"a\",\n    \"b\",\n)\n",
        );
        let framework = script.block_statements(script.all(), "framework");
        assert_eq!(chains(&script, &framework), ["baseName", "isStatic"]);
        let top = script.statements(script.all());
        let Form::Assign { value } = &top[1].form else { panic!("not an assignment") };
        assert_eq!(script.literal(value.clone()), Some(Literal::List(vec!["a".into(), "b".into()])));
    }

    #[test]
    fn finds_calls_at_any_depth_but_not_methods() {
        let script = Script::parse("dependencies { implementation(project(\":core\")); other.project(\":no\") }");
        let calls = script.calls(script.all(), "project");
        assert_eq!(calls.len(), 1);
        assert_eq!(script.string_args(&calls[0].1), Some(vec![":core".to_owned()]));
    }

    #[test]
    fn keeps_compound_operators_apart_from_assignments() {
        let script = Script::parse("a += 1\nb == c\nd = e != f\n");
        let forms: Vec<_> =
            script.statements(script.all()).into_iter().map(|s| matches!(s.form, Form::Assign { .. })).collect();
        assert_eq!(forms, [false, false, true]);
    }

    #[test]
    fn reports_lines_and_byte_spans() {
        let script = Script::parse("a = 1\n\nb = \"two\"\n");
        let statements = script.statements(script.all());
        assert_eq!(script.line(statements[1].tokens.start), 3);
        let Form::Assign { value } = &statements[1].form else { panic!("not an assignment") };
        assert_eq!(&script.source[script.byte_span(value)], "\"two\"");
    }
}
