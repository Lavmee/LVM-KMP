use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::sync::mpsc::{self, Receiver};

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use lvm_tui::app::{App, EditorRequest};
use lvm_tui::check;
use lvm_tui::doctor::Environment;
use lvm_tui::event::{self, Message};
use lvm_tui::gradle::Runner;
use lvm_tui::project::{Project, discover};
use lvm_tui::state::State;
use lvm_tui::{LVM_VERSION, ui};
use ratatui::DefaultTerminal;

/// A terminal app for Gradle builds that use LVM's convention plugins.
#[derive(Parser)]
#[command(name = "lvm-tui", version = LVM_VERSION)]
struct Cli {
    #[command(subcommand)]
    command: Option<Command>,
    /// A directory of the build; lvm-tui walks up to its settings script. Defaults to the current directory.
    path: Option<PathBuf>,
}

#[derive(Subcommand)]
enum Command {
    /// Prints what lvm-tui reads from the build as JSON, without starting the interface.
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
}

fn main() -> ExitCode {
    let cli = Cli::parse();
    let result = match cli.command {
        Some(Command::Report { path }) => report(path.or(cli.path)).map(|()| true),
        Some(Command::Check { path, json, fix }) => check(path.or(cli.path), check::Options { json, fix }),
        None => interactive(cli.path).map(|()| true),
    };
    match result {
        Ok(true) => ExitCode::SUCCESS,
        Ok(false) => ExitCode::FAILURE,
        Err(error) => {
            eprintln!("lvm-tui: {error:#}");
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

fn interactive(path: Option<PathBuf>) -> Result<()> {
    let project = find_project(path)?;
    let root = project.root.clone();
    let (sender, receiver) = mpsc::channel();
    let runner = Runner::new(&root, sender.clone());
    let mut app = App::new(project, runner, &State::load(&root));
    event::spawn_environment(root.clone(), sender.clone());
    event::spawn_input(sender);

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
