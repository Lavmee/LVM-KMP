//! The Tasks tab: targets and their tasks on the left; the log, the tests, detekt's issues or the history on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, List, ListItem, Paragraph, Wrap};

use crate::app::App;
use crate::gradle::Outcome;
use crate::results::junit::Status;
use crate::results::log::{self, LineKind};
use crate::tasks::{ResultsPane, TasksFocus};

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let [left, right] = Layout::horizontal([Constraint::Length(34), Constraint::Fill(1)]).areas(area);
    let target_rows = (app.task_targets().len() as u16 + 2).min(left.height / 2);
    let [targets, tasks] = Layout::vertical([Constraint::Length(target_rows), Constraint::Fill(1)]).areas(left);
    draw_targets(frame, app, targets);
    draw_tasks(frame, app, tasks);
    let [results, command_area] = if app.tasks.command.is_some() {
        Layout::vertical([Constraint::Fill(1), Constraint::Length(3)]).areas(right)
    } else {
        [right, Rect::default()]
    };
    match app.tasks.pane {
        ResultsPane::Log => draw_log(frame, app, results),
        ResultsPane::Tests => draw_tests(frame, app, results),
        ResultsPane::Detekt => draw_detekt(frame, app, results),
        ResultsPane::History => draw_history(frame, app, results),
    }
    if let Some(command) = &app.tasks.command {
        let line = Line::from(vec!["./gradlew ".dim(), Span::raw(command.clone()), "_".slow_blink()]);
        let hint = " enter runs, esc cancels ";
        frame.render_widget(
            Paragraph::new(line).block(Block::bordered().title(" Command ").title_bottom(hint)),
            command_area,
        );
    }
}

fn block(title: String, focused: bool) -> Block<'static> {
    let block = Block::bordered().title(title);
    if focused { block.border_style(Style::new().cyan()) } else { block }
}

fn draw_targets(frame: &mut Frame, app: &mut App, area: Rect) {
    let items: Vec<ListItem> = app
        .task_targets()
        .iter()
        .map(|target| match target {
            None => ListItem::new("whole build".italic()),
            Some(module) => ListItem::new(module.path.clone()),
        })
        .collect();
    let list = List::new(items)
        .block(block(" Run in ".to_owned(), app.tasks.focus == TasksFocus::Targets))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list, area, &mut app.tasks.targets);
}

fn draw_tasks(frame: &mut Frame, app: &mut App, area: Rect) {
    let tasks = app.visible_tasks();
    let items: Vec<ListItem> = tasks.iter().map(|task| ListItem::new(task.clone())).collect();
    let title = match (app.tasks.filtering, app.tasks.filter.is_empty()) {
        (true, _) => format!(" Tasks /{}_ ", app.tasks.filter),
        (false, false) => format!(" Tasks /{} ", app.tasks.filter),
        (false, true) => format!(" Tasks ({}) ", tasks.len()),
    };
    let list = List::new(items)
        .block(block(title, app.tasks.focus == TasksFocus::Tasks).title_bottom(" enter runs  c command "))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list, area, &mut app.tasks.tasks);
}

/// The panes as a title, with the current one highlighted and counts where they help.
fn pane_title(app: &App) -> Line<'static> {
    let failed = app.failed_tests();
    let labels = [
        (ResultsPane::Log, "Log".to_owned()),
        (
            ResultsPane::Tests,
            if failed > 0 {
                format!("Tests ({failed} failed)")
            } else {
                format!("Tests ({})", app.results.tests.len())
            },
        ),
        (ResultsPane::Detekt, format!("Detekt ({})", app.results.issues.len())),
        (ResultsPane::History, format!("History ({})", app.runs.len())),
    ];
    let mut spans = vec![Span::raw(" ")];
    for (pane, label) in labels {
        let span = Span::raw(format!(" {label} "));
        spans.push(if pane == app.tasks.pane { span.reversed() } else { span.dim() });
    }
    spans.push(" v ".dim());
    Line::from(spans)
}

fn draw_log(frame: &mut Frame, app: &App, area: Rect) {
    let run = match app.tasks.run {
        Some(index) => app.runs.get(index),
        None => app.runs.last(),
    };
    let focused = app.tasks.focus == TasksFocus::Results;
    let Some(run) = run else {
        let text = Paragraph::new(vec![
            Line::from("No build yet.".dim()),
            Line::from(""),
            Line::from("Pick a task and press enter, or c to type a command.".dim()),
        ]);
        frame.render_widget(text.block(block(String::new(), focused).title(pane_title(app))), area);
        return;
    };
    let state = match &run.outcome {
        None => "running".yellow(),
        Some((Outcome::Succeeded, elapsed)) => format!("done in {}s", elapsed.as_secs()).green(),
        Some((Outcome::Cancelled, _)) => "cancelled".yellow(),
        Some((Outcome::Failed(_), elapsed)) => format!("failed after {}s", elapsed.as_secs()).red(),
        Some((Outcome::NotStarted(reason), _)) => format!("not started: {reason}").red(),
    };
    let height = area.height.saturating_sub(2) as usize;
    let count = run.lines.len();
    let top =
        if app.tasks.follow { count.saturating_sub(height) } else { app.tasks.scroll.min(count.saturating_sub(1)) };
    let lines: Vec<Line> = run
        .lines
        .iter()
        .enumerate()
        .skip(top)
        .take(height)
        .map(|(index, text)| {
            let span = Span::raw(text.clone());
            let span = match log::classify(text) {
                LineKind::Error => span.red(),
                LineKind::Warning => span.yellow(),
                LineKind::Task => span.dim(),
                LineKind::Success => span.green().bold(),
                LineKind::Failure => span.red().bold(),
                LineKind::Plain => span,
            };
            Line::from(if app.tasks.cursor == Some(index) { span.reversed() } else { span })
        })
        .collect();
    let follow = if app.tasks.follow { " following " } else { " f follows " };
    let bottom = Line::from(vec![
        Span::raw(format!(" ./gradlew {} ", run.command)).dim(),
        state,
        Span::raw(format!("  n/N errors  o open {follow}")).dim(),
    ]);
    frame.render_widget(
        Paragraph::new(lines).block(block(String::new(), focused).title(pane_title(app)).title_bottom(bottom)),
        area,
    );
}

fn draw_tests(frame: &mut Frame, app: &mut App, area: Rect) {
    let focused = app.tasks.focus == TasksFocus::Results;
    if app.results.tests.is_empty() {
        let text = Paragraph::new("No test results yet: run a test task such as jvmTest or allTests.".dim());
        frame.render_widget(text.block(block(String::new(), focused).title(pane_title(app))), area);
        return;
    }
    let [list, detail] = Layout::vertical([Constraint::Fill(1), Constraint::Length(8)]).areas(area);
    let items: Vec<ListItem> = app
        .results
        .tests
        .iter()
        .map(|test| {
            let status = match test.status {
                Status::Failed => "FAIL ".red().bold(),
                Status::Skipped => "SKIP ".yellow(),
                Status::Passed => "pass ".green(),
            };
            ListItem::new(Line::from(vec![
                status,
                Span::raw(format!("{}.{}", test.class.rsplit('.').next().unwrap_or(&test.class), test.name)),
                Span::raw(format!("  {} {} {:.2}s", test.module, test.task, test.seconds)).dim(),
            ]))
        })
        .collect();
    let list_widget = List::new(items)
        .block(
            block(String::new(), focused)
                .title(pane_title(app))
                .title_bottom(" o open  t run again  a run failed again "),
        )
        .highlight_style(Style::new().reversed());
    frame.render_stateful_widget(list_widget, list, &mut app.tasks.tests);

    let Some(test) = app.tasks.tests.selected().and_then(|i| app.results.tests.get(i)) else { return };
    let mut lines = vec![Line::from(Span::raw(test.filter()).bold())];
    if let Some(message) = &test.message {
        lines.push(Line::from(message.clone().red()));
    }
    if let Some(trace) = &test.trace {
        lines.extend(trace.lines().skip(1).take(4).map(|line| Line::from(line.to_owned().dim())));
    }
    frame.render_widget(Paragraph::new(lines).wrap(Wrap { trim: false }).block(Block::bordered()), detail);
}

fn draw_detekt(frame: &mut Frame, app: &mut App, area: Rect) {
    let focused = app.tasks.focus == TasksFocus::Results;
    if app.results.issues.is_empty() {
        let text = Paragraph::new("No detekt issues in the last reports: detekt writes them when it runs.".dim());
        frame.render_widget(text.block(block(String::new(), focused).title(pane_title(app))), area);
        return;
    }
    let root = app.project.root.clone();
    let items: Vec<ListItem> = app
        .results
        .issues
        .iter()
        .map(|issue| {
            let file = issue.file.strip_prefix(&root).unwrap_or(&issue.file).display().to_string();
            ListItem::new(Line::from(vec![
                Span::raw(format!("{:<28}", issue.rule)).bold(),
                Span::raw(format!("{file}:{}  ", issue.line)).dim(),
                Span::raw(issue.message.clone()),
            ]))
        })
        .collect();
    let list = List::new(items)
        .block(block(String::new(), focused).title(pane_title(app)).title_bottom(" o open in $EDITOR "))
        .highlight_style(Style::new().reversed());
    frame.render_stateful_widget(list, area, &mut app.tasks.issues);
}

fn draw_history(frame: &mut Frame, app: &mut App, area: Rect) {
    let focused = app.tasks.focus == TasksFocus::Results;
    let items: Vec<ListItem> = app
        .runs
        .iter()
        .rev()
        .map(|run| {
            let outcome = match &run.outcome {
                None => "running  ".yellow(),
                Some((Outcome::Succeeded, elapsed)) => format!("ok {:>4}s  ", elapsed.as_secs()).green(),
                Some((Outcome::Cancelled, _)) => "cancelled".yellow(),
                Some((_, elapsed)) => format!("failed {:>2}s", elapsed.as_secs()).red(),
            };
            ListItem::new(Line::from(vec![outcome, Span::raw(format!("  ./gradlew {}", run.command))]))
        })
        .collect();
    let list = List::new(items)
        .block(block(String::new(), focused).title(pane_title(app)).title_bottom(" enter shows the log "))
        .highlight_style(Style::new().reversed());
    frame.render_stateful_widget(list, area, &mut app.tasks.history);
}
