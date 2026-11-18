//! Test results in the JUnit XML that Gradle writes to `build/test-results/<task>/`, for JVM, Android host and
//! Kotlin/Native tests alike.

use std::fs;
use std::path::{Path, PathBuf};

use quick_xml::Reader;
use quick_xml::escape::resolve_predefined_entity;
use quick_xml::events::{BytesStart, Event};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Status {
    Failed,
    Skipped,
    Passed,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TestCase {
    pub module: String,
    /// The test task, such as `jvmTest` or `testDebugUnitTest`.
    pub task: String,
    /// The class without the task prefix that Kotlin/Native adds, as `--tests` names it.
    pub class: String,
    /// The test without the target suffix that Kotlin/Native adds, such as `[iosSimulatorArm64]`.
    pub name: String,
    pub seconds: f64,
    pub status: Status,
    pub message: Option<String>,
    pub trace: Option<String>,
}

impl TestCase {
    /// The `--tests` filter for this test.
    pub fn filter(&self) -> String {
        format!("{}.{}", self.class, self.name)
    }

    /// The file name and line of the test class's own frame in the stack trace, such as `GreetingTest.kt:21`.
    pub fn failure_frame(&self) -> Option<(String, usize)> {
        let trace = self.trace.as_deref()?;
        let simple = self.class.rsplit('.').next().unwrap_or(&self.class);
        trace
            .lines()
            .map(str::trim)
            .filter(|line| line.starts_with("at "))
            .find(|line| line.contains(&format!("{}.", self.class)) || line.contains(&format!("{simple}.")))
            .and_then(|line| {
                let (path, number) = super::log::location(line)?;
                Some((path.file_name()?.to_string_lossy().into_owned(), number))
            })
    }
}

/// Every result under `build/test-results` of [module_dir], failed first.
pub fn load(module: &str, module_dir: &Path) -> Vec<TestCase> {
    let mut cases = Vec::new();
    let results = module_dir.join("build/test-results");
    for task in fs::read_dir(&results).into_iter().flatten().filter_map(Result::ok) {
        let task_name = task.file_name().to_string_lossy().into_owned();
        let mut files: Vec<PathBuf> = fs::read_dir(task.path())
            .into_iter()
            .flatten()
            .filter_map(Result::ok)
            .map(|e| e.path())
            .filter(|p| p.extension().is_some_and(|ext| ext == "xml"))
            .collect();
        files.sort();
        for file in files {
            if let Ok(text) = fs::read_to_string(&file) {
                cases.extend(parse(module, &task_name, &text));
            }
        }
    }
    cases.sort_by(|a, b| (a.status, &a.task, &a.class, &a.name).cmp(&(b.status, &b.task, &b.class, &b.name)));
    cases
}

/// The test cases of one JUnit XML file.
pub fn parse(module: &str, task: &str, xml: &str) -> Vec<TestCase> {
    let mut reader = Reader::from_str(xml);
    let mut cases = Vec::new();
    let mut current: Option<TestCase> = None;
    // The text of the <failure> or <error> element being read.
    let mut text: Option<String> = None;
    loop {
        match reader.read_event() {
            Ok(Event::Start(element)) | Ok(Event::Empty(element)) if element.local_name().as_ref() == "testcase" => {
                if let Some(done) = current.take() {
                    cases.push(done);
                }
                current = Some(test_case(module, task, &element));
            }
            Ok(Event::Start(element)) => match element.local_name().as_ref() {
                "failure" | "error" => {
                    if let Some(case) = current.as_mut() {
                        case.status = Status::Failed;
                        case.message = attribute(&element, "message");
                        text = Some(String::new());
                    }
                }
                "skipped" => mark_skipped(current.as_mut()),
                _ => {}
            },
            Ok(Event::Empty(element)) => match element.local_name().as_ref() {
                "failure" | "error" => {
                    if let Some(case) = current.as_mut() {
                        case.status = Status::Failed;
                        case.message = attribute(&element, "message");
                    }
                }
                "skipped" => mark_skipped(current.as_mut()),
                _ => {}
            },
            Ok(Event::Text(content)) => {
                if let Some(text) = text.as_mut() {
                    text.push_str(&content.xml10_content());
                }
            }
            Ok(Event::CData(content)) => {
                if let Some(text) = text.as_mut() {
                    text.push_str(&content.xml10_content());
                }
            }
            Ok(Event::GeneralRef(reference)) => {
                if let Some(text) = text.as_mut() {
                    match reference.resolve_char_ref() {
                        Ok(Some(c)) => text.push(c),
                        _ => text.push_str(resolve_predefined_entity(&reference.xml10_content()).unwrap_or("")),
                    }
                }
            }
            Ok(Event::End(element)) => match element.local_name().as_ref() {
                "failure" | "error" => {
                    if let (Some(case), Some(trace)) = (current.as_mut(), text.take()) {
                        case.trace = Some(trace.trim().to_owned());
                    }
                }
                "testcase" => {
                    if let Some(done) = current.take() {
                        cases.push(done);
                    }
                }
                _ => {}
            },
            Ok(Event::Eof) | Err(_) => break,
            Ok(_) => {}
        }
    }
    if let Some(done) = current.take() {
        cases.push(done);
    }
    cases
}

fn mark_skipped(case: Option<&mut TestCase>) {
    if let Some(case) = case
        && case.status == Status::Passed
    {
        case.status = Status::Skipped;
    }
}

fn test_case(module: &str, task: &str, element: &BytesStart) -> TestCase {
    let class = attribute(element, "classname").unwrap_or_default();
    let name = attribute(element, "name").unwrap_or_default();
    // Kotlin/Native names its classes after the task and its tests after the target.
    let class = class.strip_prefix(&format!("{task}.")).map(str::to_owned).unwrap_or(class);
    let name = match name.rfind('[') {
        Some(at) if name.ends_with(']') => name[..at].to_owned(),
        _ => name,
    };
    TestCase {
        module: module.to_owned(),
        task: task.to_owned(),
        class,
        name,
        seconds: attribute(element, "time").and_then(|t| t.parse().ok()).unwrap_or(0.0),
        status: Status::Passed,
        message: None,
        trace: None,
    }
}

fn attribute(element: &BytesStart, name: &str) -> Option<String> {
    element.attributes().filter_map(Result::ok).find(|attribute| attribute.key.local_name().as_ref() == name).and_then(
        |attribute| attribute.normalized_value(quick_xml::XmlVersion::Implicit1_0).ok().map(|value| value.into_owned()),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    const JVM: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.GreetingTest" tests="3" skipped="1" failures="1" errors="0" time="0.1">
  <properties/>
  <testcase name="greets by name" classname="com.example.GreetingTest" time="0.012">
    <failure message="expected:&lt;Hi&gt; but was:&lt;Hello&gt;" type="java.lang.AssertionError">java.lang.AssertionError: expected:&lt;Hi&gt; but was:&lt;Hello&gt;
	at kotlin.test.Asserter.fail(Assertions.kt:10)
	at com.example.GreetingTest.greets by name(GreetingTest.kt:21)
</failure>
  </testcase>
  <testcase name="greets nobody" classname="com.example.GreetingTest" time="0.0">
    <skipped/>
  </testcase>
  <testcase name="greets everybody" classname="com.example.GreetingTest" time="0.003"/>
  <system-out><![CDATA[]]></system-out>
</testsuite>
"#;

    #[test]
    fn reads_passed_failed_and_skipped_tests() {
        let cases = parse(":shared", "jvmTest", JVM);
        let statuses: Vec<_> = cases.iter().map(|c| (c.name.as_str(), c.status)).collect();
        assert_eq!(
            statuses,
            [
                ("greets by name", Status::Failed),
                ("greets nobody", Status::Skipped),
                ("greets everybody", Status::Passed)
            ]
        );
        let failed = &cases[0];
        assert_eq!(failed.message.as_deref(), Some("expected:<Hi> but was:<Hello>"));
        assert!(failed.trace.as_deref().unwrap().starts_with("java.lang.AssertionError: expected:<Hi>"));
        assert_eq!(failed.failure_frame(), Some(("GreetingTest.kt".to_owned(), 21)));
        assert_eq!(failed.filter(), "com.example.GreetingTest.greets by name");
    }

    #[test]
    fn drops_the_names_that_kotlin_native_adds() {
        let xml = r#"<testsuite name="iosSimulatorArm64Test.com.example.GreetingTest">
  <testcase name="greets[iosSimulatorArm64]" classname="iosSimulatorArm64Test.com.example.GreetingTest" time="0.0"/>
</testsuite>"#;
        let cases = parse(":shared", "iosSimulatorArm64Test", xml);
        assert_eq!(cases[0].class, "com.example.GreetingTest");
        assert_eq!(cases[0].name, "greets");
    }

    #[test]
    fn loads_every_task_of_a_module_failed_first() {
        let dir = tempfile::tempdir().unwrap();
        for (task, file, xml) in [
            ("jvmTest", "TEST-a.xml", JVM),
            (
                "testAndroidHostTest",
                "TEST-b.xml",
                "<testsuite><testcase name=\"t\" classname=\"B\" time=\"1\"/></testsuite>",
            ),
        ] {
            fs::create_dir_all(dir.path().join("build/test-results").join(task)).unwrap();
            fs::write(dir.path().join("build/test-results").join(task).join(file), xml).unwrap();
        }
        let cases = load(":shared", dir.path());
        assert_eq!(cases.len(), 4);
        assert_eq!(cases[0].status, Status::Failed);
        assert!(cases.iter().any(|c| c.task == "testAndroidHostTest"));
    }
}
