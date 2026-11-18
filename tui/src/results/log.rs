//! Lines of Gradle's console output: what kind each one is, and the source location it points at.

use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LineKind {
    Error,
    Warning,
    /// `> Task :app:compileKotlin`
    Task,
    Success,
    Failure,
    Plain,
}

/// What a line of Gradle's output is.
pub fn classify(line: &str) -> LineKind {
    let trimmed = line.trim_start();
    if trimmed.starts_with("BUILD SUCCESSFUL") {
        LineKind::Success
    } else if trimmed.starts_with("BUILD FAILED")
        || trimmed.starts_with("FAILURE:")
        || trimmed.starts_with("* What went wrong")
    {
        LineKind::Failure
    } else if trimmed.starts_with("e: ")
        || trimmed.ends_with(" FAILED")
        || line.contains(": error:")
        || line.contains(": error: ")
        || (location(line).is_some()
            && line.contains("[")
            && line.trim_end().ends_with(']')
            && !trimmed.starts_with("w: "))
    {
        LineKind::Error
    } else if trimmed.starts_with("w: ") || line.contains(": warning:") {
        LineKind::Warning
    } else if trimmed.starts_with("> Task ") {
        LineKind::Task
    } else {
        LineKind::Plain
    }
}

/// Whether the user would want to jump to the line: an error or a failed test.
pub fn is_problem(line: &str) -> bool {
    matches!(classify(line), LineKind::Error | LineKind::Failure)
}

/// The file and line a line of output points at, from the forms Kotlin, Java, detekt and stack traces print:
/// `e: file:///a/B.kt:12:5 message`, `/a/B.kt: (12, 5): message`, `B.java:12: error:`, `/a/B.kt:12:5: message [Rule]`
/// and `at com.example.B.test(B.kt:12)`.
pub fn location(line: &str) -> Option<(PathBuf, usize)> {
    let words: Vec<&str> = line.split_whitespace().collect();
    for (index, word) in words.iter().enumerate() {
        let word = word.trim_start_matches("file://").trim_start_matches('(');
        for extension in [".kt", ".kts", ".java"] {
            let Some(at) = word.find(&format!("{extension}:")) else { continue };
            let path = &word[..at + extension.len()];
            let rest = &word[at + extension.len() + 1..];
            // A stack frame names a method before the file: at com.example.B.test(B.kt:12)
            let path = path.rsplit('(').next().unwrap_or(path);
            let number: String = rest.chars().take_while(char::is_ascii_digit).collect();
            let line_number = if number.is_empty() {
                // The old Kotlin form puts the position in the next word: /a/B.kt: (12, 5)
                words.get(index + 1).map(|next| {
                    next.trim_start_matches('(').chars().take_while(char::is_ascii_digit).collect::<String>()
                })
            } else {
                Some(number)
            };
            if let Some(line_number) = line_number.and_then(|n| n.parse().ok()) {
                return Some((PathBuf::from(path), line_number));
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_locations_in_every_form() {
        let at = |line: &str| location(line).map(|(path, line)| (path.display().to_string(), line));
        assert_eq!(
            at("e: file:///work/app/src/A.kt:12:5 Unresolved reference 'x'."),
            Some(("/work/app/src/A.kt".into(), 12))
        );
        assert_eq!(at("e: /work/app/src/A.kt: (7, 3): Expecting ')'"), Some(("/work/app/src/A.kt".into(), 7)));
        assert_eq!(at("/work/lib/B.java:40: error: cannot find symbol"), Some(("/work/lib/B.java".into(), 40)));
        assert_eq!(at("/work/lib/C.kt:3:1: The file is empty. [EmptyKotlinFile]"), Some(("/work/lib/C.kt".into(), 3)));
        assert_eq!(
            at("    at com.example.GreetingTest.greets(GreetingTest.kt:21)"),
            Some(("GreetingTest.kt".into(), 21))
        );
        assert_eq!(at("> Task :app:compileKotlin"), None);
    }

    #[test]
    fn classifies_errors_warnings_and_outcomes() {
        assert_eq!(classify("e: file:///a/B.kt:1:1 boom"), LineKind::Error);
        assert_eq!(classify("w: file:///a/B.kt:1:1 deprecated"), LineKind::Warning);
        assert_eq!(classify("GreetingTest > greets() FAILED"), LineKind::Error);
        assert_eq!(classify("/a/C.kt:3:1: The file is empty. [EmptyKotlinFile]"), LineKind::Error);
        assert_eq!(classify("> Task :app:check"), LineKind::Task);
        assert_eq!(classify("BUILD SUCCESSFUL in 3s"), LineKind::Success);
        assert_eq!(classify("BUILD FAILED in 3s"), LineKind::Failure);
        assert_eq!(classify("Configuration cache entry reused."), LineKind::Plain);
    }
}
