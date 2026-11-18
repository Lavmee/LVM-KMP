use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::sync::mpsc::{self, Receiver};

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use lvm_tui::app::{App, EditorRequest};
use lvm_tui::check;
use lvm_tui::doctor::Environment;
use lvm_tui::edit::ChangeSet;
use lvm_tui::event::{self as events, Message};
use lvm_tui::form::{Form, FormEvent};
use lvm_tui::gradle::Runner;
use lvm_tui::project::{Project, discover};
use lvm_tui::scaffold;
use lvm_tui::state::State;
use lvm_tui::wizard::{self, ProjectDefaults};
use lvm_tui::{LVM_VERSION, ui};
use ratatui::DefaultTerminal;
use ratatui::crossterm::event;
use ratatui::crossterm::event::{Event, KeyCode, KeyEventKind, KeyModifiers};

/// A terminal app for Gradle builds that use LVM's convention plugins.
#[derive(Parser)]
#[command(name = "lvm", version = LVM_VERSION)]
struct Cli {
    #[command(subcommand)]
    command: Option<Command>,
    /// A directory of the build; lvm walks up to its settings script. Defaults to the current directory.
    path: Option<PathBuf>,
}

#[derive(Subcommand)]
enum Command {
    /// Prints what lvm reads from the build as JSON, without starting the interface.
    Report {
        /// A directory of the build. Defaults to the current directory.
        path: Option<PathBuf>,
    },
    /// Checks the build for what fails LVM's plugins. Exits with 1 while errors remain.
    Check {
        /// A directory of the build. Defaults to the current directory.
        path: Option<PathBuf>,
        /// Prints the findings as JSON.
        #[arg(long)]
        json: bool,
        /// Applies every fix the findings have, then checks again.
        #[arg(long)]
        fix: bool,
    },
    /// Creates a build with LVM: a shared Kotlin Multiplatform library and an Android app that uses it.
    New(NewArgs),
    /// Moves the build to another LVM release, by default the latest on Maven Central.
    Upgrade {
        /// A directory of the build. Defaults to the current directory.
        path: Option<PathBuf>,
        /// The LVM version to move to.
        #[arg(long)]
        to: Option<String>,
        /// Shows the change without writing it.
        #[arg(long)]
        dry_run: bool,
    },
}

#[derive(clap::Args)]
struct NewArgs {
    /// Where the build goes; created when missing. Defaults to the current directory.
    dir: Option<PathBuf>,
    /// rootProject.name. Defaults to the directory's name.
    #[arg(long)]
    name: Option<String>,
    /// The group and package of the modules.
    #[arg(long, default_value = "com.example")]
    group: String,
    /// The targets of :shared.
    #[arg(long, value_delimiter = ',', default_value = "android,ios,jvm")]
    targets: Vec<String>,
    /// Leaves out the Android app.
    #[arg(long)]
    no_app: bool,
    /// Applies tech.annexflow.lvm.compose.
    #[arg(long)]
    compose: bool,
    /// Leaves out tech.annexflow.lvm.quality.
    #[arg(long)]
    no_quality: bool,
    /// Uses src/<sourceSet>/kotlin instead of LVM's flat layout.
    #[arg(long)]
    standard_layout: bool,
    /// Creates the build without asking.
    #[arg(long)]
    yes: bool,
    /// The LVM version; the latest on Maven Central by default.
    #[arg(long, hide = true)]
    lvm_version: Option<String>,
    /// Resolves LVM from Maven local first, to try a version published there.
    #[arg(long, hide = true)]
    maven_local: bool,
}

fn main() -> ExitCode {
    let cli = Cli::parse();
    let result = match cli.command {
        Some(Command::Report { path }) => report(path.or(cli.path)).map(|()| true),
        Some(Command::Check { path, json, fix }) => check(path.or(cli.path), check::Options { json, fix }),
        Some(Command::Upgrade { path, to, dry_run }) => {
            upgrade(path.or(cli.path), to.as_deref(), dry_run).map(|()| true)
        }
        Some(Command::New(args)) => new_build(args).map(|()| true),
        None => interactive(cli.path).map(|()| true),
    };
    match result {
        Ok(true) => ExitCode::SUCCESS,
        Ok(false) => ExitCode::FAILURE,
        Err(error) => {
            eprintln!("lvm: {error:#}");
            ExitCode::FAILURE
        }
    }
}

fn find_project(path: Option<PathBuf>) -> Result<Project> {
    let start = path.unwrap_or_else(|| PathBuf::from("."));
    let root = discover::find_root(&start)
        .with_context(|| format!("no Gradle build at or above {}: there is no settings.gradle.kts", start.display()))?;
    Ok(Project::load(&root))
}

fn report(path: Option<PathBuf>) -> Result<()> {
    let project = find_project(path)?;
    println!("{}", serde_json::to_string_pretty(&project)?);
    Ok(())
}

/// Returns whether the build passed: no errors remain.
fn check(path: Option<PathBuf>, options: check::Options) -> Result<bool> {
    let project = find_project(path)?;
    let env = Environment::probe(&project.root);
    let errors = check::run(project, &env, options, &mut std::io::stdout().lock())?;
    Ok(!errors)
}

fn upgrade(path: Option<PathBuf>, to: Option<&str>, dry_run: bool) -> Result<()> {
    let project = find_project(path)?;
    let releases = lvm_tui::maven::Releases::maven_central();
    lvm_tui::upgrade::run(&project, &releases, to, dry_run, &mut std::io::stdout().lock())
}

fn defaults(args: &NewArgs) -> Result<ProjectDefaults> {
    let cwd = std::env::current_dir()?;
    let dir = args.dir.as_ref().map_or_else(|| cwd.clone(), |dir| cwd.join(dir));
    let lvm_version = match &args.lvm_version {
        Some(version) => version.clone(),
        // A release of today is missing from a list cached yesterday, so the command asks each time.
        None => lvm_tui::maven::Releases::maven_central()
            .versions(true)
            .ok()
            .and_then(|versions| versions.last().cloned())
            .unwrap_or_else(|| LVM_VERSION.to_owned()),
    };
    let name =
        args.name.clone().unwrap_or_else(|| dir.file_name().map_or("app".into(), |n| n.to_string_lossy().into_owned()));
    Ok(ProjectDefaults {
        dir,
        name,
        group: args.group.clone(),
        lvm_version,
        targets: args.targets.clone(),
        app: !args.no_app,
        compose: args.compose,
        quality: !args.no_quality,
        flat: !args.standard_layout,
        maven_local: args.maven_local,
    })
}

fn new_build(args: NewArgs) -> Result<()> {
    let defaults = defaults(&args)?;
    if args.yes {
        let changes = scaffold::new_project(&defaults.dir, &defaults.spec())?;
        changes.apply(&defaults.dir)?;
        for file in changes.files.keys().chain(changes.binaries.keys()) {
            println!("created {}", defaults.dir.join(file).display());
        }
        println!("Next: cd {} && ./gradlew build", defaults.dir.display());
        return Ok(());
    }
    if let Some(dir) = wizard(defaults)? {
        interactive(Some(dir))?;
    }
    Ok(())
}

/// What the wizard shows: the form, then the files it creates.
enum Step {
    Form(Form),
    Confirm { form: Form, dir: PathBuf, changes: ChangeSet, preview: Vec<String>, scroll: usize },
}

/// Asks for the build in a form and creates it; returns its directory, or `None` when the user leaves.
fn wizard(defaults: ProjectDefaults) -> Result<Option<PathBuf>> {
    let mut terminal = ratatui::init();
    let result = run_wizard(&mut terminal, &defaults);
    ratatui::restore();
    result
}

fn run_wizard(terminal: &mut DefaultTerminal, defaults: &ProjectDefaults) -> Result<Option<PathBuf>> {
    let mut step = Step::Form(wizard::project_form(defaults));
    loop {
        terminal.draw(|frame| match &step {
            Step::Form(form) => ui::draw_form(frame, form),
            Step::Confirm { preview, scroll, dir, .. } => {
                ui::draw_changes(frame, &format!("Create the build in {}", dir.display()), preview, *scroll)
            }
        })?;
        let Event::Key(key) = event::read()? else { continue };
        if key.kind == KeyEventKind::Release {
            continue;
        }
        if key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL) {
            return Ok(None);
        }
        step = match step {
            Step::Form(mut form) => match form.key(key) {
                FormEvent::Cancel => return Ok(None),
                FormEvent::Submit => {
                    let defaults = wizard::project_defaults(&form);
                    match scaffold::new_project(&defaults.dir, &defaults.spec()) {
                        Ok(changes) => {
                            let preview = changes.preview().lines().map(str::to_owned).collect();
                            Step::Confirm { form, dir: defaults.dir, changes, preview, scroll: 0 }
                        }
                        Err(error) => {
                            form.error = Some(format!("{error:#}"));
                            Step::Form(form)
                        }
                    }
                }
                _ => Step::Form(form),
            },
            Step::Confirm { form, dir, changes, preview, scroll } => {
                let last = preview.len().saturating_sub(1);
                let scroll = match key.code {
                    KeyCode::Char('y') | KeyCode::Enter => {
                        changes.apply(&dir)?;
                        return Ok(Some(dir));
                    }
                    KeyCode::Char('n') | KeyCode::Esc => {
                        step = Step::Form(form);
                        continue;
                    }
                    KeyCode::Down | KeyCode::Char('j') => (scroll + 1).min(last),
                    KeyCode::Up | KeyCode::Char('k') => scroll.saturating_sub(1),
                    KeyCode::PageDown | KeyCode::Char(' ') => (scroll + 20).min(last),
                    KeyCode::PageUp => scroll.saturating_sub(20),
                    _ => scroll,
                };
                Step::Confirm { form, dir, changes, preview, scroll }
            }
        };
    }
}

fn interactive(path: Option<PathBuf>) -> Result<()> {
    let start = path.clone().unwrap_or_else(|| PathBuf::from("."));
    if discover::find_root(&start).is_none() {
        // Outside a build, lvm-tui offers to create one.
        let args = NewArgs {
            dir: path,
            name: None,
            group: "com.example".into(),
            targets: vec!["android".into(), "ios".into(), "jvm".into()],
            no_app: false,
            compose: false,
            no_quality: false,
            standard_layout: false,
            yes: false,
            lvm_version: None,
            maven_local: false,
        };
        return match wizard(defaults(&args)?)? {
            Some(dir) => interactive(Some(dir)),
            None => Ok(()),
        };
    }
    let project = find_project(Some(start))?;

    let root = project.root.clone();
    let (sender, receiver) = mpsc::channel();
    let runner = Runner::new(&root, sender.clone());
    let mut app = App::new(project, runner, &State::load(&root));
    events::spawn_environment(root.clone(), sender.clone());
    events::spawn_input(sender);

    let mut terminal = ratatui::init();
    let result = run(&mut terminal, &mut app, &receiver);
    ratatui::restore();
    app.state().save(Path::new(&root));
    result
}

fn run(terminal: &mut DefaultTerminal, app: &mut App, messages: &Receiver<Message>) -> Result<()> {
    while !app.quit {
        terminal.draw(|frame| ui::draw(frame, app))?;
        let Ok(message) = messages.recv() else { break };
        app.update(message);
        // Handle what queued up meanwhile before drawing again, so a burst of Gradle output costs one frame.
        while let Ok(message) = messages.try_recv() {
            app.update(message);
        }
        if let Some(request) = app.editor.take() {
            ratatui::restore();
            let result = open_in_editor(&request);
            *terminal = ratatui::init();
            match result {
                Ok(()) => app.reload(),
                Err(error) => app.status = Some(format!("{error:#}")),
            }
        }
    }
    Ok(())
}

/// Runs `$VISUAL` or `$EDITOR`, at the line where the editor takes one, while lvm-tui leaves it the terminal.
fn open_in_editor(request: &EditorRequest) -> Result<()> {
    let editor = std::env::var("VISUAL").or_else(|_| std::env::var("EDITOR")).unwrap_or_else(|_| "vi".to_owned());
    let mut words = editor.split_whitespace();
    let program = words.next().context("$EDITOR is empty")?;
    let mut command = std::process::Command::new(program);
    command.args(words);
    let file = request.file.display().to_string();
    let name = Path::new(program).file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    match request.line {
        // VS Code and its relatives take file:line with -g; Sublime and Zed take file:line.
        Some(line) if name.starts_with("code") || name.starts_with("cursor") => {
            command.args(["--wait", "-g", &format!("{file}:{line}")]);
        }
        Some(line) if name == "subl" || name == "zed" => {
            command.arg(format!("{file}:{line}"));
        }
        Some(line) => {
            command.args([format!("+{line}"), file]);
        }
        None => {
            command.arg(file);
        }
    }
    let status = command.status().with_context(|| format!("starting {editor}"))?;
    if !status.success() {
        anyhow::bail!("{editor} exited with {status}");
    }
    Ok(())
}
