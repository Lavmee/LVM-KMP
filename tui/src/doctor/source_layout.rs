//! The packages of a module with the flat layout, checked as blueprint's `lvmCheckSourceLayout` checks them: the root
//! package is the longest package that every Kotlin and Java file shares, and a file in `<code root>/a/b/` declares
//! `<root package>.a.b`.

use std::fs;
use std::path::Path;

/// A Kotlin or Java file in a code root of the flat layout.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SourceFile {
    /// The code root, such as `src` or `test@jvm`.
    pub root: String,
    /// The directories between the code root and the file.
    pub directories: Vec<String>,
    pub name: String,
    /// The declared package and the 1-based line of its declaration, `None` for a file without one.
    pub package: Option<(String, usize)>,
}

impl SourceFile {
    /// The file's path in the module, with `/`.
    pub fn path(&self) -> String {
        let mut segments = vec![self.root.as_str()];
        segments.extend(self.directories.iter().map(String::as_str));
        segments.push(&self.name);
        segments.join("/")
    }

    fn declared(&self) -> Option<&str> {
        self.package.as_ref().map(|(name, _)| name.as_str())
    }
}

/// A file outside the directory of its package.
pub struct Misplaced<'a> {
    pub file: &'a SourceFile,
    /// The package that the file's directory stands for.
    pub expected: String,
    /// Whether the directories start with the root package, as in `src/com/example/` for `com.example`.
    pub repeats_root: bool,
}

pub struct SourceLayout {
    /// The root package by segment, empty when the files share none.
    pub root: Vec<String>,
    pub files: Vec<SourceFile>,
}

impl SourceLayout {
    /// Reads the Kotlin and Java files of the flat layout's directories in [module_dir]: `src`, `test`, `androidTest`
    /// and `testFixtures`, each also followed by `@<name>`. Lvm-tui doesn't know the module's source sets, so a
    /// directory such as `src@unused` counts too. `module-info.java` declares a module rather than a package and is
    /// left out.
    pub fn read(module_dir: &Path) -> Self {
        let mut roots: Vec<String> = fs::read_dir(module_dir)
            .into_iter()
            .flatten()
            .filter_map(Result::ok)
            .filter(|entry| entry.path().is_dir())
            .map(|entry| entry.file_name().to_string_lossy().into_owned())
            .filter(|name| is_code_root(name))
            .collect();
        roots.sort();
        let mut files = Vec::new();
        for root in roots {
            collect(&module_dir.join(&root), &root, &mut Vec::new(), &mut files);
        }
        Self::new(files)
    }

    pub fn new(mut files: Vec<SourceFile>) -> Self {
        files.sort_by_key(SourceFile::path);
        let root = files
            .iter()
            .filter_map(|file| file.declared().map(|name| name.split('.').map(str::to_owned).collect::<Vec<_>>()))
            .reduce(|shared, package| {
                shared.into_iter().zip(package).take_while(|(a, b)| a == b).map(|(a, _)| a).collect()
            })
            .unwrap_or_default();
        Self { root, files }
    }

    pub fn root_package(&self) -> String {
        self.root.join(".")
    }

    pub fn misplaced(&self) -> Vec<Misplaced<'_>> {
        self.files
            .iter()
            .filter_map(|file| {
                let expected = self.root.iter().chain(&file.directories).cloned().collect::<Vec<_>>().join(".");
                if file.declared() == Some(expected.as_str()) {
                    return None;
                }
                let repeats_root = !self.root.is_empty() && file.directories.starts_with(&self.root);
                Some(Misplaced { file, expected, repeats_root })
            })
            .collect()
    }

    /// Files that declare the package of their directories alone, such as `ui` for `src/ui/Screen.kt`, as IDE
    /// templates do. With one of them the files share no root package, and the others look misplaced.
    pub fn without_root_package(&self) -> Vec<&SourceFile> {
        if !self.root.is_empty() || self.misplaced().is_empty() {
            return Vec::new();
        }
        self.files
            .iter()
            .filter(|file| !file.directories.is_empty() && file.declared() == Some(file.directories.join(".").as_str()))
            .collect()
    }
}

fn is_code_root(name: &str) -> bool {
    ["src", "test", "androidTest", "testFixtures"]
        .iter()
        .any(|root| name.strip_prefix(root).is_some_and(|rest| rest.is_empty() || rest.starts_with('@')))
}

fn collect(dir: &Path, root: &str, directories: &mut Vec<String>, files: &mut Vec<SourceFile>) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for entry in entries.filter_map(Result::ok) {
        let name = entry.file_name().to_string_lossy().into_owned();
        let path = entry.path();
        if path.is_dir() {
            directories.push(name);
            collect(&path, root, directories, files);
            directories.pop();
        } else if (name.ends_with(".kt") || name.ends_with(".java")) && name != "module-info.java" {
            let source = fs::read_to_string(&path).unwrap_or_default();
            let package = declared_package(&source, name.ends_with(".kt"));
            files.push(SourceFile { root: root.to_owned(), directories: directories.clone(), name, package });
        }
    }
}

/// The package that a Kotlin or Java file declares and the line of its declaration, read without a compiler: the name
/// after the first `package`, past a byte order mark, a shebang line, comments and annotations, such as Kotlin's
/// `@file:JvmName("Names")`. Backticks around a segment are dropped. Kotlin's block comments nest, which
/// [nested_comments] turns on.
pub fn declared_package(source: &str, nested_comments: bool) -> Option<(String, usize)> {
    Reader { chars: source.chars().collect(), position: 0, nested_comments }.read()
}

struct Reader {
    chars: Vec<char>,
    position: usize,
    nested_comments: bool,
}

impl Reader {
    fn read(mut self) -> Option<(String, usize)> {
        if self.peek(0) == Some('\u{feff}') {
            self.position += 1;
        }
        if self.at("#!") {
            self.skip_line();
        }
        loop {
            self.skip_trivia();
            if self.peek(0) == Some('@') {
                self.skip_annotation();
            } else if self.at_keyword("package") {
                let line = 1 + self.chars[..self.position].iter().filter(|c| **c == '\n').count();
                self.position += "package".len();
                self.skip_trivia();
                return self.qualified_name().map(|name| (name.join("."), line));
            } else {
                return None;
            }
        }
    }

    fn peek(&self, offset: usize) -> Option<char> {
        self.chars.get(self.position + offset).copied()
    }

    fn at(&self, text: &str) -> bool {
        text.chars().enumerate().all(|(i, c)| self.peek(i) == Some(c))
    }

    fn at_keyword(&self, keyword: &str) -> bool {
        self.at(keyword) && !self.peek(keyword.chars().count()).is_some_and(is_identifier_part)
    }

    fn skip_trivia(&mut self) {
        loop {
            if self.peek(0).is_some_and(char::is_whitespace) {
                self.position += 1;
            } else if self.at("//") {
                self.skip_line();
            } else if self.at("/*") {
                self.skip_block_comment();
            } else {
                return;
            }
        }
    }

    fn skip_line(&mut self) {
        while self.peek(0).is_some_and(|c| c != '\n') {
            self.position += 1;
        }
    }

    fn skip_block_comment(&mut self) {
        self.position += 2;
        let mut depth = 1;
        while depth > 0 && self.peek(0).is_some() {
            if self.at("*/") {
                depth -= 1;
                self.position += 2;
            } else if self.nested_comments && self.at("/*") {
                depth += 1;
                self.position += 2;
            } else {
                self.position += 1;
            }
        }
    }

    /// Skips `@Name`, `@a.b.Name(arguments)`, Kotlin's `@file:Name(arguments)` and `@file:[A B(arguments)]`.
    fn skip_annotation(&mut self) {
        self.position += 1;
        let name = self.qualified_name();
        self.skip_trivia();
        if self.peek(0) == Some(':') && name.is_some_and(|name| name == ["file"]) {
            self.position += 1;
            self.skip_trivia();
            if self.peek(0) == Some('[') {
                self.skip_balanced('[', ']');
                return;
            }
            self.qualified_name();
            self.skip_trivia();
        }
        if self.peek(0) == Some('(') {
            self.skip_balanced('(', ')');
        }
    }

    /// Skips from [open] to its matching [close], past strings, characters and comments that may hold either.
    fn skip_balanced(&mut self, open: char, close: char) {
        let mut depth = 0;
        while let Some(c) = self.peek(0) {
            if self.at("//") {
                self.skip_line();
            } else if self.at("/*") {
                self.skip_block_comment();
            } else if self.at("\"\"\"") {
                self.skip_raw_string();
            } else if c == '"' || c == '\'' {
                self.skip_quoted(c);
            } else if c == open {
                depth += 1;
                self.position += 1;
            } else if c == close {
                self.position += 1;
                depth -= 1;
                if depth == 0 {
                    return;
                }
            } else {
                self.position += 1;
            }
        }
    }

    fn skip_raw_string(&mut self) {
        self.position += 3;
        while self.peek(0).is_some() && !self.at("\"\"\"") {
            self.position += 1;
        }
        self.position += 3;
        // A raw string may end in more quotes than three, as in """a"""".
        while self.peek(0) == Some('"') {
            self.position += 1;
        }
    }

    fn skip_quoted(&mut self, quote: char) {
        self.position += 1;
        while let Some(c) = self.peek(0) {
            match c {
                '\n' => return,
                '\\' => self.position += 2,
                _ if c == quote => {
                    self.position += 1;
                    return;
                }
                _ => self.position += 1,
            }
        }
    }

    /// Identifiers separated by dots, with whitespace and comments around the dots, as Kotlin allows. It stops before
    /// anything else, such as `;` or the next line's `import`, and is `None` when no identifier comes first.
    fn qualified_name(&mut self) -> Option<Vec<String>> {
        let mut segments = vec![self.identifier()?];
        loop {
            let before_dot = self.position;
            self.skip_trivia();
            if self.peek(0) != Some('.') {
                self.position = before_dot;
                return Some(segments);
            }
            self.position += 1;
            self.skip_trivia();
            match self.identifier() {
                Some(segment) => segments.push(segment),
                None => {
                    self.position = before_dot;
                    return Some(segments);
                }
            }
        }
    }

    fn identifier(&mut self) -> Option<String> {
        if self.peek(0) == Some('`') {
            let rest = &self.chars[self.position + 1..];
            let end = rest.iter().position(|c| *c == '`' || *c == '\n')?;
            if rest[end] != '`' {
                return None;
            }
            let name: String = rest[..end].iter().collect();
            self.position += end + 2;
            return Some(name);
        }
        if !self.peek(0).is_some_and(is_identifier_start) {
            return None;
        }
        let start = self.position;
        while self.peek(0).is_some_and(is_identifier_part) {
            self.position += 1;
        }
        Some(self.chars[start..self.position].iter().collect())
    }
}

fn is_identifier_start(c: char) -> bool {
    c.is_alphabetic() || c == '_' || c == '$'
}

fn is_identifier_part(c: char) -> bool {
    c.is_alphanumeric() || c == '_' || c == '$'
}

#[cfg(test)]
mod tests {
    use super::*;

    fn kotlin(source: &str) -> Option<String> {
        declared_package(source, true).map(|(name, _)| name)
    }

    fn file(path: &str, package: Option<&str>) -> SourceFile {
        let mut segments: Vec<String> = path.split('/').map(str::to_owned).collect();
        let name = segments.pop().unwrap();
        let root = segments.remove(0);
        SourceFile { root, directories: segments, name, package: package.map(|p| (p.to_owned(), 1)) }
    }

    fn misplaced(layout: &SourceLayout) -> Vec<(String, String)> {
        layout.misplaced().iter().map(|m| (m.file.path(), m.expected.clone())).collect()
    }

    #[test]
    fn reads_the_package_and_its_line_past_comments_and_file_annotations() {
        let source = "/* Copyright /* nested */ package not.this */\n// package nor.this\n\
                      @file:JvmName(\"Names(\\\")\")\n@file:[JvmMultifileClass OptIn(A::class)]\n\
                      package com.example.`in`;\n\nimport kotlin.math.max\n";
        assert_eq!(declared_package(source, true), Some(("com.example.in".to_owned(), 5)));
    }

    #[test]
    fn reads_java_comments_without_nesting_and_package_info_annotations() {
        let source = "/* /* not nested in Java */\n@Deprecated\n@SuppressWarnings({\"a\"})\npackage com.example;";
        assert_eq!(declared_package(source, false), Some(("com.example".to_owned(), 4)));
    }

    #[test]
    fn reads_a_name_across_lines_only_where_a_dot_continues_it() {
        assert_eq!(kotlin("package com.example\nimport kotlin.math.max"), Some("com.example".to_owned()));
        assert_eq!(kotlin("package com.example\n    .feature\n\nclass A"), Some("com.example.feature".to_owned()));
        assert_eq!(kotlin("\u{feff}#!/usr/bin/env kotlin\npackage com.example"), Some("com.example".to_owned()));
    }

    #[test]
    fn finds_no_package_when_code_or_imports_come_first() {
        assert_eq!(kotlin("import kotlin.math.max\nclass A"), None);
        assert_eq!(kotlin("class A\npackage com.example"), None);
        assert_eq!(kotlin("val packages = 1"), None);
        assert_eq!(kotlin(""), None);
    }

    #[test]
    fn the_root_package_is_the_longest_that_every_code_root_shares() {
        let layout = SourceLayout::new(vec![
            file("src/Chat.kt", Some("com.example.chat")),
            file("src@jvm/ui/Window.kt", Some("com.example.chat.ui")),
            file("test/data/RepositoryTest.kt", Some("com.example.chat.data")),
            file("src/Util.kt", None),
        ]);
        assert_eq!(layout.root_package(), "com.example.chat");
        assert_eq!(misplaced(&layout), [("src/Util.kt".to_owned(), "com.example.chat".to_owned())]);
    }

    #[test]
    fn finds_files_in_another_package_and_directories_that_repeat_the_root_package() {
        let layout = SourceLayout::new(vec![
            file("src/Chat.kt", Some("com.example.chat")),
            file("src/ui/Model.kt", Some("com.example.chat.data")),
            file("src/com/example/chat/ui/Screen.kt", Some("com.example.chat.ui")),
        ]);
        let wrong = layout.misplaced();
        assert_eq!(
            misplaced(&layout).iter().map(|(path, _)| path.as_str()).collect::<Vec<_>>(),
            ["src/com/example/chat/ui/Screen.kt", "src/ui/Model.kt"]
        );
        assert!(wrong[0].repeats_root);
        assert_eq!(wrong[1].expected, "com.example.chat.ui");
    }

    #[test]
    fn names_files_whose_package_is_their_directory_alone() {
        let layout = SourceLayout::new(vec![
            file("src/Chat.kt", Some("com.example.chat")),
            file("src/ui/Screen.kt", Some("ui")),
        ]);
        assert!(layout.root.is_empty());
        assert_eq!(layout.without_root_package().iter().map(|f| f.path()).collect::<Vec<_>>(), ["src/ui/Screen.kt"]);
    }

    #[test]
    fn reads_only_the_flat_directories_of_a_module() {
        let dir = tempfile::tempdir().unwrap();
        for (path, source) in [
            ("src/ui/Screen.kt", "package com.example.ui"),
            ("src@jvm/Main.java", "package com.example;"),
            ("build/generated/ksp/Generated.kt", "package generated"),
            ("srcExtra/Other.kt", "package other"),
            ("src/module-info.java", "module com.example {}"),
        ] {
            let file = dir.path().join(path);
            fs::create_dir_all(file.parent().unwrap()).unwrap();
            fs::write(file, source).unwrap();
        }
        let layout = SourceLayout::read(dir.path());
        let paths: Vec<String> = layout.files.iter().map(SourceFile::path).collect();
        assert_eq!(paths, ["src/ui/Screen.kt", "src@jvm/Main.java"]);
    }
}
