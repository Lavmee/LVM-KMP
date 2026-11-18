use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::sync::mpsc::{self, Receiver};

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use lvm_tui::app::App;
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
}

fn main() -> ExitCode {
    let cli = Cli::parse();
    let result = match cli.command {
        Some(Command::Report { path }) => report(path.or(cli.path)),
        None => interactive(cli.path),
    };
    match result {
        Ok(()) => ExitCode::SUCCESS,
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

fn interactive(path: Option<PathBuf>) -> Result<()> {
    let project = find_project(path)?;
    let root = project.root.clone();
    let (sender, receiver) = mpsc::channel();
    let runner = Runner::new(&root, sender.clone());
    let mut app = App::new(project, runner, &State::load(&root));
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
    }
    Ok(())
}
