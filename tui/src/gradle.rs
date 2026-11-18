//! Runs Gradle in the background, one build at a time, and streams its output.

use std::collections::VecDeque;
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::Sender;
use std::thread;
use std::time::{Duration, Instant};

pub type JobId = u64;

/// What a running build reports.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum GradleEvent {
    Started { job: JobId, command: String },
    Line { job: JobId, line: String },
    Finished { job: JobId, outcome: Outcome, elapsed: Duration },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Outcome {
    Succeeded,
    Failed(Option<i32>),
    Cancelled,
    /// Gradle could not be started at all.
    NotStarted(String),
}

/// A build to run: Gradle arguments such as `:app:check`, and what the caller needs to know when it finishes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Job {
    pub id: JobId,
    pub args: Vec<String>,
    pub purpose: Purpose,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Purpose {
    /// `lvmReport`, after which the project is read again.
    Report,
    Task,
}

/// Runs [Job]s one after another in the build at its root, sending their events to [events].
pub struct Runner<E: From<GradleEvent> + Send + 'static> {
    root: PathBuf,
    events: Sender<E>,
    queue: VecDeque<Job>,
    running: Option<Running>,
    next_id: JobId,
    executable: Option<PathBuf>,
}

struct Running {
    job: Job,
    cancel: Arc<AtomicBool>,
}

impl<E: From<GradleEvent> + Send + 'static> Runner<E> {
    pub fn new(root: &Path, events: Sender<E>) -> Self {
        Self { root: root.to_owned(), events, queue: VecDeque::new(), running: None, next_id: 1, executable: None }
    }

    /// Runs Gradle with [executable] instead of the wrapper, as tests do.
    pub fn with_executable(mut self, executable: PathBuf) -> Self {
        self.executable = Some(executable);
        self
    }

    /// Queues a build and starts it when nothing else runs.
    pub fn submit(&mut self, args: Vec<String>, purpose: Purpose) -> JobId {
        let id = self.next_id;
        self.next_id += 1;
        self.queue.push_back(Job { id, args, purpose });
        self.start_next();
        id
    }

    /// Where the runner sends its events, for other background work of the app.
    pub fn sender(&self) -> Sender<E> {
        self.events.clone()
    }

    pub fn running(&self) -> Option<&Job> {
        self.running.as_ref().map(|running| &running.job)
    }

    pub fn queued(&self) -> usize {
        self.queue.len()
    }

    /// Stops the running build. Its `Finished` event arrives once the process has exited.
    pub fn cancel(&mut self) {
        if let Some(running) = &self.running {
            running.cancel.store(true, Ordering::SeqCst);
        }
    }

    /// To be called with every `Finished` event: forgets the build and starts the next one.
    pub fn finished(&mut self, job: JobId) -> Option<Job> {
        let finished = self.running.take_if(|running| running.job.id == job).map(|running| running.job);
        self.start_next();
        finished
    }

    fn start_next(&mut self) {
        if self.running.is_some() {
            return;
        }
        let Some(job) = self.queue.pop_front() else { return };
        let cancel = Arc::new(AtomicBool::new(false));
        let executable = self.executable.clone().unwrap_or_else(|| gradle_executable(&self.root));
        spawn(&self.root, &executable, job.clone(), cancel.clone(), self.events.clone());
        self.running = Some(Running { job, cancel });
    }
}

/// The wrapper of the build, or `gradle` from the `PATH` for a build without one.
pub fn gradle_executable(root: &Path) -> PathBuf {
    let wrapper = if cfg!(windows) { "gradlew.bat" } else { "gradlew" };
    let path = root.join(wrapper);
    if path.is_file() { path } else { PathBuf::from("gradle") }
}

fn spawn<E: From<GradleEvent> + Send + 'static>(
    root: &Path,
    executable: &Path,
    job: Job,
    cancel: Arc<AtomicBool>,
    events: Sender<E>,
) {
    let mut args = job.args.clone();
    args.push("--console=plain".to_owned());
    // The arguments as the user would type them after ./gradlew.
    let command = job.args.join(" ");
    let started = Instant::now();
    let child = Command::new(executable)
        .args(&args)
        .current_dir(root)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn();
    let send = move |event: GradleEvent| {
        let _ = events.send(event.into());
    };
    let mut child = match child {
        Ok(child) => child,
        Err(error) => {
            let outcome = Outcome::NotStarted(format!("{}: {error}", executable.display()));
            send(GradleEvent::Finished { job: job.id, outcome, elapsed: started.elapsed() });
            return;
        }
    };
    send(GradleEvent::Started { job: job.id, command });
    let readers: Vec<_> = [
        child.stdout.take().map(|s| Box::new(s) as Box<dyn Read + Send>),
        child.stderr.take().map(|s| Box::new(s) as Box<dyn Read + Send>),
    ]
    .into_iter()
    .flatten()
    .map(|stream| {
        let send = send.clone();
        let id = job.id;
        thread::spawn(move || {
            for line in BufReader::new(stream).lines().map_while(Result::ok) {
                send(GradleEvent::Line { job: id, line });
            }
        })
    })
    .collect();
    thread::spawn(move || {
        let outcome = wait(&mut child, &cancel);
        // A cancelled build may leave a process behind that holds the output open; its last lines don't matter.
        if outcome != Outcome::Cancelled {
            for reader in readers {
                let _ = reader.join();
            }
        }
        send(GradleEvent::Finished { job: job.id, outcome, elapsed: started.elapsed() });
    });
}

/// Waits for [child], killing it once [cancel] is set. The Gradle daemon cancels the build when its client goes away.
fn wait(child: &mut Child, cancel: &AtomicBool) -> Outcome {
    loop {
        match child.try_wait() {
            Ok(Some(status)) if status.success() => return Outcome::Succeeded,
            Ok(Some(status)) => {
                return if cancel.load(Ordering::SeqCst) { Outcome::Cancelled } else { Outcome::Failed(status.code()) };
            }
            Ok(None) if cancel.load(Ordering::SeqCst) => {
                let _ = child.kill();
                let _ = child.wait();
                return Outcome::Cancelled;
            }
            Ok(None) => thread::sleep(Duration::from_millis(50)),
            Err(error) => return Outcome::NotStarted(error.to_string()),
        }
    }
}

#[cfg(all(test, unix))]
mod tests {
    use super::*;
    use std::os::unix::fs::PermissionsExt;
    use std::sync::mpsc::{Receiver, channel};

    fn fake_gradle(dir: &Path, script: &str) -> PathBuf {
        let path = dir.join("fake-gradle");
        std::fs::write(&path, format!("#!/bin/sh\n{script}\n")).unwrap();
        std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755)).unwrap();
        path
    }

    fn until_finished(events: &Receiver<GradleEvent>) -> Vec<GradleEvent> {
        let mut received = Vec::new();
        loop {
            let event = events.recv_timeout(Duration::from_secs(10)).expect("an event");
            let done = matches!(event, GradleEvent::Finished { .. });
            received.push(event);
            if done {
                return received;
            }
        }
    }

    #[test]
    fn streams_lines_and_reports_the_exit_code() {
        let dir = tempfile::tempdir().unwrap();
        let gradle = fake_gradle(dir.path(), "echo \"args: $*\"\necho oops >&2\nexit 3");
        let (sender, events) = channel();
        let mut runner = Runner::new(dir.path(), sender).with_executable(gradle);

        let job = runner.submit(vec![":app:check".into()], Purpose::Task);
        let received = until_finished(&events);

        assert!(received.contains(&GradleEvent::Line { job, line: "args: :app:check --console=plain".into() }));
        assert!(received.contains(&GradleEvent::Line { job, line: "oops".into() }));
        assert!(matches!(received.last(), Some(GradleEvent::Finished { outcome: Outcome::Failed(Some(3)), .. })));
    }

    #[test]
    fn runs_queued_builds_one_after_another() {
        let dir = tempfile::tempdir().unwrap();
        let gradle = fake_gradle(dir.path(), "echo \"$1\"");
        let (sender, events) = channel();
        let mut runner = Runner::new(dir.path(), sender).with_executable(gradle);

        let first = runner.submit(vec!["first".into()], Purpose::Task);
        let second = runner.submit(vec!["second".into()], Purpose::Report);
        assert_eq!(runner.queued(), 1);
        until_finished(&events);
        assert_eq!(runner.finished(first).map(|job| job.purpose), Some(Purpose::Task));
        assert_eq!(runner.running().map(|job| job.id), Some(second));
        let received = until_finished(&events);

        assert!(received.contains(&GradleEvent::Line { job: second, line: "second".into() }));
        assert_eq!(runner.finished(second).map(|job| job.purpose), Some(Purpose::Report));
        assert!(runner.running().is_none());
    }

    #[test]
    fn cancelling_stops_the_process() {
        let dir = tempfile::tempdir().unwrap();
        let gradle = fake_gradle(dir.path(), "echo started\nexec sleep 30");
        let (sender, events) = channel();
        let mut runner = Runner::new(dir.path(), sender).with_executable(gradle);

        runner.submit(vec!["build".into()], Purpose::Task);
        while !matches!(events.recv_timeout(Duration::from_secs(10)).unwrap(), GradleEvent::Line { .. }) {}
        runner.cancel();
        let received = until_finished(&events);

        assert!(matches!(received.last(), Some(GradleEvent::Finished { outcome: Outcome::Cancelled, .. })));
    }

    #[test]
    fn a_missing_executable_is_reported() {
        let dir = tempfile::tempdir().unwrap();
        let (sender, events) = channel();
        let mut runner = Runner::new(dir.path(), sender).with_executable(dir.path().join("missing"));

        runner.submit(vec!["build".into()], Purpose::Task);

        assert!(matches!(
            until_finished(&events).last(),
            Some(GradleEvent::Finished { outcome: Outcome::NotStarted(_), .. })
        ));
    }
}
