//! The Run tab: apps and devices on the left, the app's log on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, List, ListItem, Paragraph};

use crate::app::App;
use crate::applog::{Level, LogLine};
use crate::devices::State;
use crate::run::RunKind;
use crate::run_tab::RunFocus;

fn block(title: String, focused: bool) -> Block<'static> {
    let block = Block::bordered().title(title);
    if focused { block.border_style(Style::new().cyan()) } else { block }
}

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let apps_width = app.run.runnables.iter().map(|r| r.label().len()).max().unwrap_or(0);
    // Every device, not only those of the selected app, so the column keeps its width.
    let devices_width = app.run.devices.iter().flatten().map(|d| d.name.len() + 9).max().unwrap_or(0);
    let width = apps_width.max(devices_width) as u16 + 6;
    let [left, log] = Layout::horizontal([Constraint::Length(width.clamp(32, 56)), Constraint::Fill(1)]).areas(area);
    let [apps, devices] = Layout::vertical([Constraint::Percentage(40), Constraint::Fill(1)]).areas(left);

    let items: Vec<ListItem> = app.run.runnables.iter().map(|r| ListItem::new(r.label())).collect();
    let empty = items.is_empty();
    let list = List::new(items)
        .block(block(" Apps ".to_owned(), app.run.focus == RunFocus::Targets))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    if empty {
        let text = "No Android app, iOS framework or JVM app in the build.".dim();
        frame.render_widget(
            Paragraph::new(Line::from(text))
                .wrap(ratatui::widgets::Wrap { trim: true })
                .block(block(" Apps ".to_owned(), app.run.focus == RunFocus::Targets)),
            apps,
        );
    } else {
        frame.render_stateful_widget(list, apps, &mut app.run.targets);
    }

    draw_devices(frame, app, devices);
    draw_log(frame, app, log);
}

fn draw_devices(frame: &mut Frame, app: &mut App, area: Rect) {
    let focused = app.run.focus == RunFocus::Devices;
    let note = match app.selected_runnable().map(|r| r.kind) {
        Some(RunKind::Jvm { .. }) => Some("Runs on this computer.".to_owned()),
        Some(RunKind::IosFramework) => {
            Some("Builds the framework for the simulator: the build has no Xcode project to run.".to_owned())
        }
        None => Some(String::new()),
        _ if app.run.devices.is_none() => Some("Looking for devices...".to_owned()),
        _ if app.platform_devices().is_empty() => {
            Some("No device: connect one, or create an emulator or a simulator, then press f.".to_owned())
        }
        _ => None,
    };
    let title = " Devices ".to_owned();
    if let Some(note) = note {
        let paragraph = Paragraph::new(Line::from(note.dim())).wrap(ratatui::widgets::Wrap { trim: true });
        frame.render_widget(paragraph.block(block(title, focused)), area);
        return;
    }
    let items: Vec<ListItem> = app
        .platform_devices()
        .into_iter()
        .map(|device| {
            let state = match &device.state {
                State::Ready => "running".green(),
                State::Stopped => "stopped".dim(),
                State::Unusable(reason) => Span::raw(reason.clone()).red(),
            };
            ListItem::new(Line::from(vec![Span::raw(format!("{}  ", device.name)), state]))
        })
        .collect();
    let list = List::new(items)
        .block(block(title, focused).title_bottom(" s start   f look again "))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list, area, &mut app.run.devices_list);
}

fn draw_log(frame: &mut Frame, app: &App, area: Rect) {
    let view = &app.run;
    let mut title = vec![Span::raw(" Log")];
    match &view.source {
        Some(source) => title.push(Span::raw(format!(": {source} "))),
        None => title.push(Span::raw(" ")),
    }
    if view.min_level != Level::Verbose {
        title.push(format!("{}+ ", view.min_level.letter()).yellow());
    }
    if !view.search.is_empty() || view.searching {
        let cursor = if view.searching { "_" } else { "" };
        title.push(format!("/{}{cursor} ", view.search).cyan());
    }
    if view.paused {
        title.push("paused ".yellow());
    }
    if let Some(reason) = &view.ended {
        title.push(format!("ended: {reason} ").dim());
    }
    let keys = " a app log   F log file   L level   / search   space pause   C clear   x stop ";
    let block = block(String::new(), view.focus == RunFocus::Log).title(Line::from(title)).title_bottom(keys);
    let inner = block.inner(area);
    frame.render_widget(block, area);

    let [input, lines_area] = if view.file_input.is_some() {
        Layout::vertical([Constraint::Length(2), Constraint::Fill(1)]).areas(inner)
    } else {
        Layout::vertical([Constraint::Length(0), Constraint::Fill(1)]).areas(inner)
    };
    if let Some(path) = &view.file_input {
        let lines = vec![
            Line::from(vec!["Log file: ".bold(), Span::raw(path.clone()), "_".slow_blink()]),
            Line::from("An absolute path here, or a path in the Android app's files, such as files/logs/app.log".dim()),
        ];
        frame.render_widget(Paragraph::new(lines), input);
    }

    let visible = view.visible();
    if visible.is_empty() {
        let text = if view.source.is_some() {
            vec![Line::from("Waiting for the app to log...".dim())]
        } else {
            vec![
                Line::from("enter runs the selected app on the selected device.".dim()),
                Line::from("a follows its log: logcat on Android, the unified log on a simulator.".dim()),
                Line::from("F follows a file that lvm-logger-file writes.".dim()),
                Line::from("A JVM app's output shows here when enter runs it.".dim()),
            ]
        };
        frame.render_widget(Paragraph::new(text), lines_area);
        return;
    }
    let height = lines_area.height as usize;
    let bottom = if view.paused { view.scroll.min(visible.len() - 1) + 1 } else { visible.len() };
    let tag_width = visible.iter().map(|l| l.tag.chars().count()).max().unwrap_or(0).min(24);
    let lines: Vec<Line> =
        visible[bottom.saturating_sub(height)..bottom].iter().map(|line| log_line(line, tag_width)).collect();
    frame.render_widget(Paragraph::new(lines), lines_area);
}

fn log_line(line: &LogLine, tag_width: usize) -> Line<'static> {
    let level = Span::raw(format!("{} ", line.level.letter()));
    let level = match line.level {
        Level::Verbose => level.dim(),
        Level::Debug => level.blue(),
        Level::Info => level.green(),
        Level::Warn => level.yellow(),
        Level::Error => level.red(),
        Level::Assert => level.red().bold(),
    };
    let message = match line.level {
        Level::Error | Level::Assert => Span::raw(line.message.clone()).red(),
        Level::Warn => Span::raw(line.message.clone()).yellow(),
        _ => Span::raw(line.message.clone()),
    };
    let mut spans = Vec::new();
    if !line.time.is_empty() {
        spans.push(Span::raw(format!("{} ", line.time)).dim());
    }
    spans.push(level);
    if tag_width > 0 {
        let tag: String = line.tag.chars().take(tag_width).collect();
        spans.push(Span::raw(format!("{tag:<tag_width$} ")).cyan());
    }
    spans.push(message);
    Line::from(spans)
}
