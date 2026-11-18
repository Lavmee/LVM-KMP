//! An app's log while it runs: Android's logcat, the iOS simulator's unified log, or the files that lvm-logger-file
//! writes, on the computer or on an Android device.

use std::fs;
use std::io::{BufRead, BufReader, Read, Seek, SeekFrom};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::Sender;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::event::Message;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert,
}

impl Level {
    pub const ALL: [Level; 6] = [Level::Verbose, Level::Debug, Level::Info, Level::Warn, Level::Error, Level::Assert];

    pub fn letter(self) -> char {
        match self {
            Self::Verbose => 'V',
            Self::Debug => 'D',
            Self::Info => 'I',
            Self::Warn => 'W',
            Self::Error => 'E',
            Self::Assert => 'A',
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LogLine {
    pub time: String,
    pub level: Level,
    pub tag: String,
    pub message: String,
}

impl LogLine {
    fn plain(text: &str) -> Self {
        Self { time: String::new(), level: Level::Info, tag: String::new(), message: text.to_owned() }
    }

    pub fn matches(&self, search: &str) -> bool {
        let search = search.to_lowercase();
        search.is_empty() || self.tag.to_lowercase().contains(&search) || self.message.to_lowercase().contains(&search)
    }
}

/// `logcat -v threadtime`: `11-18 10:15:31.861  1234  1256 I Tag     : message`.
pub fn parse_logcat(line: &str) -> LogLine {
    let words: Vec<&str> = line.split_whitespace().take(5).collect();
    let level = match words.get(4).copied() {
        Some("V") => Level::Verbose,
        Some("D") => Level::Debug,
        Some("I") => Level::Info,
        Some("W") => Level::Warn,
        Some("E") => Level::Error,
        Some("F" | "A") => Level::Assert,
        _ => return LogLine::plain(line),
    };
    // The tag runs up to the first ": " after the level letter.
    let after_level = line.find(&format!(" {} ", words[4])).map(|at| &line[at + 3..]).unwrap_or_default();
    let (tag, message) = after_level.split_once(": ").unwrap_or(("", after_level));
    LogLine { time: words[1].to_owned(), level, tag: tag.trim().to_owned(), message: message.to_owned() }
}

/// `log stream --style compact`: `2026-11-18 10:15:31.861 E  MyApp[123:4567] [com.example:net] message`.
pub fn parse_unified(line: &str) -> LogLine {
    let mut words = line.split_whitespace();
    let (Some(_date), Some(time), Some(kind), Some(process)) = (words.next(), words.next(), words.next(), words.next())
    else {
        return LogLine::plain(line);
    };
    let level = match kind {
        "Db" => Level::Debug,
        "Df" | "I" | "In" => Level::Info,
        "E" | "Er" => Level::Error,
        "F" | "Fa" => Level::Assert,
        _ => return LogLine::plain(line),
    };
    let rest = line.split_once(process).map(|(_, rest)| rest.trim_start()).unwrap_or_default();
    let (tag, message) = match rest.strip_prefix('[').and_then(|r| r.split_once("] ")) {
        Some((category, message)) => (category.to_owned(), message.to_owned()),
        None => (process.split('[').next().unwrap_or(process).to_owned(), rest.to_owned()),
    };
    LogLine { time: time.to_owned(), level, tag, message }
}

/// lvm-logger-file's lines, as Kermit formats them: `2026-11-18T10:15:31.861+03:00 Info: (Tag) message`, the time
/// only when the logger prepends it.
pub fn parse_kermit(line: &str) -> LogLine {
    let (time, rest) = match line.split_once(' ') {
        Some((first, rest)) if first.starts_with(|c: char| c.is_ascii_digit()) && first.contains('T') => {
            let time = first.split('T').nth(1).unwrap_or(first);
            (time.chars().take(12).collect::<String>(), rest)
        }
        _ => (String::new(), line),
    };
    let names = [
        ("Verbose", Level::Verbose),
        ("Debug", Level::Debug),
        ("Info", Level::Info),
        ("Warn", Level::Warn),
        ("Error", Level::Error),
        ("Assert", Level::Assert),
    ];
    let Some((level, rest)) =
        names.iter().find_map(|(name, level)| rest.strip_prefix(&format!("{name}: ")).map(|rest| (*level, rest)))
    else {
        // A stack trace line continues the entry before it.
        return LogLine { time, ..LogLine::plain(line) };
    };
    let (tag, message) = match rest.strip_prefix('(').and_then(|r| r.split_once(") ")) {
        Some((tag, message)) => (tag.to_owned(), message.to_owned()),
        None => (String::new(), rest.to_owned()),
    };
    LogLine { time, level, tag, message }
}

/// Where an app's log comes from.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Source {
    Logcat {
        adb: PathBuf,
        serial: String,
        application_id: String,
    },
    Simulator {
        udid: String,
        process: String,
    },
    /// A file of lvm-logger-file on this computer.
    File(PathBuf),
    /// A file in an Android app's data directory, read with `run-as`, which needs a debuggable build.
    DeviceFile {
        adb: PathBuf,
        serial: String,
        application_id: String,
        path: String,
    },
}

impl Source {
    pub fn label(&self) -> String {
        match self {
            Self::Logcat { application_id, serial, .. } => format!("logcat of {application_id} on {serial}"),
            Self::Simulator { process, .. } => format!("simulator log of {process}"),
            Self::File(path) => format!("file {}", path.display()),
            Self::DeviceFile { path, application_id, .. } => format!("file {path} of {application_id}"),
        }
    }
}

/// A log being read on its own thread. Dropping it stops the reading.
pub struct LogStream {
    cancel: Arc<AtomicBool>,
    child: Arc<Mutex<Option<Child>>>,
}

impl LogStream {
    /// Reads [source] until it ends or the stream is stopped, sending its lines tagged with [id].
    pub fn start(id: u64, source: Source, events: Sender<Message>) -> Self {
        let cancel = Arc::new(AtomicBool::new(false));
        let child: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));
        let stream = Self { cancel: cancel.clone(), child: child.clone() };
        thread::spawn(move || {
            let send = |line: LogLine| events.send(Message::AppLog { stream: id, line }).is_ok();
            let end = match &source {
                Source::Logcat { adb, serial, application_id } => {
                    let pid = Command::new(adb)
                        .args(["-s", serial, "shell", "pidof", "-s", application_id])
                        .output()
                        .ok()
                        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_owned())
                        .filter(|pid| !pid.is_empty());
                    match pid {
                        None => format!("{application_id} isn't running on {serial}: run it first"),
                        Some(pid) => {
                            let mut command = Command::new(adb);
                            command.args([
                                "-s",
                                serial,
                                "logcat",
                                "-v",
                                "threadtime",
                                "-T",
                                "500",
                                &format!("--pid={pid}"),
                            ]);
                            follow(command, &child, &cancel, parse_logcat, &send)
                        }
                    }
                }
                Source::Simulator { udid, process } => {
                    let mut command = Command::new("xcrun");
                    let predicate = format!("process == \"{process}\"");
                    command.args([
                        "simctl",
                        "spawn",
                        udid,
                        "log",
                        "stream",
                        "--style",
                        "compact",
                        "--level",
                        "debug",
                        "--predicate",
                        &predicate,
                    ]);
                    follow(command, &child, &cancel, parse_unified, &send)
                }
                Source::File(path) => tail_file(path, &cancel, &send),
                Source::DeviceFile { adb, serial, application_id, path } => {
                    poll_device_file(adb, serial, application_id, path, &cancel, &send)
                }
            };
            let _ = events.send(Message::AppLogEnded { stream: id, reason: end });
        });
        stream
    }

    pub fn stop(&self) {
        self.cancel.store(true, Ordering::SeqCst);
        if let Ok(mut child) = self.child.lock()
            && let Some(child) = child.as_mut()
        {
            let _ = child.kill();
        }
    }
}

impl Drop for LogStream {
    fn drop(&mut self) {
        self.stop();
    }
}

/// Runs [command] and sends each line of its output until it ends or the stream stops.
fn follow(
    mut command: Command,
    child: &Mutex<Option<Child>>,
    cancel: &AtomicBool,
    parse: fn(&str) -> LogLine,
    send: &dyn Fn(LogLine) -> bool,
) -> String {
    let spawned = command.stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::null()).spawn();
    let mut process = match spawned {
        Ok(process) => process,
        Err(error) => return format!("Could not start the log: {error}"),
    };
    let Some(stdout) = process.stdout.take() else { return "The log has no output".to_owned() };
    if let Ok(mut slot) = child.lock() {
        *slot = Some(process);
    }
    for line in BufReader::new(stdout).lines().map_while(Result::ok) {
        // logcat's `--------- beginning of main` marks where a buffer starts, which says nothing about the app.
        if line.starts_with("--------- ") {
            continue;
        }
        if cancel.load(Ordering::SeqCst) || !send(parse(&line)) {
            break;
        }
    }
    "The log ended".to_owned()
}

/// Sends the end of [path] and then what is written to it, starting over when the logger rolls the file.
fn tail_file(path: &PathBuf, cancel: &AtomicBool, send: &dyn Fn(LogLine) -> bool) -> String {
    const START: u64 = 64 * 1024;
    let mut position: Option<u64> = None;
    let mut partial = String::new();
    while !cancel.load(Ordering::SeqCst) {
        let Ok(mut file) = fs::File::open(path) else {
            thread::sleep(Duration::from_millis(500));
            continue;
        };
        let length = file.metadata().map(|m| m.len()).unwrap_or(0);
        // The first read shows the last part of the file; a shorter file means the logger rolled it.
        let from = match position {
            None => length.saturating_sub(START),
            Some(at) if at > length => 0,
            Some(at) => at,
        };
        if from < length && file.seek(SeekFrom::Start(from)).is_ok() {
            let mut text = String::new();
            let _ = file.take(length - from).read_to_string(&mut text);
            partial.push_str(&text);
            let mut lines: Vec<&str> = partial.split('\n').collect();
            let rest = lines.pop().unwrap_or_default().to_owned();
            for line in lines.iter().filter(|l| !l.is_empty()) {
                if !send(parse_kermit(line)) {
                    return "The log ended".to_owned();
                }
            }
            partial = rest;
        }
        position = Some(length);
        thread::sleep(Duration::from_millis(300));
    }
    "The log stopped".to_owned()
}

/// Reads [path] in the app's data directory every second with `run-as`, and sends the lines it didn't send yet.
fn poll_device_file(
    adb: &PathBuf,
    serial: &str,
    application_id: &str,
    path: &str,
    cancel: &AtomicBool,
    send: &dyn Fn(LogLine) -> bool,
) -> String {
    let mut sent = 0;
    while !cancel.load(Ordering::SeqCst) {
        let output = Command::new(adb).args(["-s", serial, "exec-out", "run-as", application_id, "cat", path]).output();
        match output {
            Ok(output) if output.status.success() => {
                let text = String::from_utf8_lossy(&output.stdout);
                let lines: Vec<&str> = text.lines().collect();
                if lines.len() < sent {
                    sent = 0;
                }
                for line in &lines[sent..] {
                    if !send(parse_kermit(line)) {
                        return "The log ended".to_owned();
                    }
                }
                sent = lines.len();
            }
            Ok(output) => {
                let error = String::from_utf8_lossy(&output.stderr).trim().to_owned();
                return format!("run-as can't read {path}: {error}. It works only for debuggable builds.");
            }
            Err(error) => return format!("Could not run adb: {error}"),
        }
        thread::sleep(Duration::from_secs(1));
    }
    "The log stopped".to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::sync::mpsc::channel;

    #[test]
    fn reads_logcat_lines() {
        let line = parse_logcat("11-18 10:15:31.861  1234  1256 W RootComponent: back pressed: twice");
        assert_eq!(
            line,
            LogLine {
                time: "10:15:31.861".into(),
                level: Level::Warn,
                tag: "RootComponent".into(),
                message: "back pressed: twice".into()
            }
        );
        assert_eq!(parse_logcat("--------- beginning of main").level, Level::Info);
    }

    #[test]
    fn reads_unified_log_lines() {
        let line = parse_unified("2026-11-18 10:15:31.861 E  iosApp[123:4567] [com.example:net] request failed");
        assert_eq!(
            (line.level, line.tag.as_str(), line.message.as_str()),
            (Level::Error, "com.example:net", "request failed")
        );
        let plain = parse_unified("2026-11-18 10:15:31.861 Df iosApp[123:4567] started");
        assert_eq!((plain.level, plain.tag.as_str(), plain.message.as_str()), (Level::Info, "iosApp", "started"));
    }

    #[test]
    fn reads_kermit_lines_with_and_without_time() {
        let line = parse_kermit("2026-11-18T10:15:31.861+03:00 Error: (Storage) disk full");
        assert_eq!(
            line,
            LogLine {
                time: "10:15:31.861".into(),
                level: Level::Error,
                tag: "Storage".into(),
                message: "disk full".into()
            }
        );
        let bare = parse_kermit("Debug: loaded");
        assert_eq!((bare.level, bare.tag.as_str(), bare.message.as_str()), (Level::Debug, "", "loaded"));
        assert_eq!(parse_kermit("\tat com.example.A.b(A.kt:3)").message, "\tat com.example.A.b(A.kt:3)");
    }

    #[test]
    fn follows_a_file_and_starts_over_when_it_rolls() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("app.log");
        fs::write(&path, "Info: (A) one\nInfo: (A) two\n").unwrap();
        let (sender, events) = channel();
        let stream = LogStream::start(1, Source::File(path.clone()), sender);
        let next = || match events.recv_timeout(Duration::from_secs(5)).unwrap() {
            Message::AppLog { line, .. } => line.message,
            _ => panic!("not a log line"),
        };
        assert_eq!((next(), next()), ("one".to_owned(), "two".to_owned()));

        fs::OpenOptions::new().append(true).open(&path).unwrap().write_all(b"Warn: (A) three\n").unwrap();
        assert_eq!(next(), "three");
        // The logger rolled the file: it starts again, shorter.
        fs::write(&path, "Info: (A) four\n").unwrap();
        assert_eq!(next(), "four");
        drop(stream);
    }
}
