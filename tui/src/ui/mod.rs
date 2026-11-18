//! Draws the app's state. Nothing here changes it, except the scroll positions that ratatui's stateful widgets keep.

mod dialog;
mod doctor;
pub(crate) mod form;
mod library;
mod modules;
mod overview;
mod tasks;
mod updates;

use ratatui::Frame;
use ratatui::layout::{Constraint, Flex, Layout, Rect};
use ratatui::style::{Color, Modifier, Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Clear, List, ListItem, ListState, Paragraph, Tabs};
use serde_json::Value;

use crate::app::{Action, App, Tab};
use crate::gradle::Outcome;
use crate::project::model::Origin;
use crate::project::report::Source;
use crate::version::Support;

const SPINNER: [&str; 4] = ["|", "/", "-", "\\"];

pub fn draw(frame: &mut Frame, app: &mut App) {
    let [header, body, status] =
        Layout::vertical([Constraint::Length(1), Constraint::Fill(1), Constraint::Length(1)]).areas(frame.area());
    draw_header(frame, app, header);
    match app.tab {
        Tab::Overview => overview::draw(frame, app, body),
        Tab::Modules => modules::draw(frame, app, body),
        Tab::Tasks => tasks::draw(frame, app, body),
        Tab::Updates => updates::draw(frame, app, body),
        Tab::Library => library::draw(frame, app, body),
        Tab::Doctor => doctor::draw(frame, app, body),
    }
    draw_status(frame, app, status);
    if app.help {
        draw_help(frame);
    }
    if app.palette.is_some() {
        draw_palette(frame, app);
    }
    if app.dialog.is_some() {
        dialog::draw(frame, app);
    }
}

fn draw_header(frame: &mut Frame, app: &App, area: Rect) {
    let tabs = app.tabs();
    let titles = tabs.iter().enumerate().map(|(i, tab)| format!("{} {}", i + 1, tab.title()));
    let selected = tabs.iter().position(|tab| *tab == app.tab);
    let [tabs, name] =
        Layout::horizontal([Constraint::Fill(1), Constraint::Length(app.project.name.len() as u16 + 14)]).areas(area);
    frame.render_widget(
        Tabs::new(titles).select(selected).highlight_style(Style::new().bold().reversed()).divider(" "),
        tabs,
    );
    let label = Line::from(vec![" lvm-tui ".dim(), Span::raw(app.project.name.clone()).bold()]).right_aligned();
    frame.render_widget(label, name);
}

fn draw_status(frame: &mut Frame, app: &App, area: Rect) {
    let mut spans = Vec::new();
    match app.runs.last() {
        Some(job) if job.outcome.is_none() => {
            let spinner = SPINNER[app.ticks % SPINNER.len()];
            spans.push(Span::raw(format!(" {spinner} {} ", job.command)).yellow());
            if let Some(line) = job.lines.iter().rev().find(|line| !line.trim().is_empty()) {
                spans.push(Span::raw(line.clone()).dim());
            }
        }
        _ => {
            if let Some(status) = &app.status {
                let style = match app.runs.last().and_then(|job| job.outcome.as_ref()) {
                    Some((Outcome::Succeeded, _)) => Style::new().green(),
                    Some((Outcome::Cancelled, _)) => Style::new().yellow(),
                    Some(_) => Style::new().red(),
                    None => Style::new(),
                };
                spans.push(Span::styled(format!(" {status}"), style));
            } else {
                let hint = match app.tab {
                    Tab::Overview => " r report   3 doctor   ctrl-p commands   ? keys   q quit",
                    Tab::Modules => {
                        " e edit   n new module   d add library   m flat layout   o open   / filter   ? keys"
                    }
                    Tab::Tasks => " enter run   c command   v log/tests/detekt/history   x cancel   ? keys",
                    Tab::Doctor => " f fix   F fix all   o open in $EDITOR   R check again   ? keys",
                    Tab::Updates => " j k release   u upgrade   f ask Maven Central again   ? keys",
                    Tab::Library => " c check ABI   u update dump   p publish to Maven local   e edit POM   ? keys",
                };
                spans.push(hint.dim());
            }
        }
    }
    frame.render_widget(Line::from(spans), area);
}

fn draw_help(frame: &mut Frame) {
    let rows: Vec<Line> = [
        ("1-6, tab", "switch tabs"),
        ("r", "run lvmReport, then read the reports"),
        ("R", "read the build files again"),
        ("ctrl-p, :", "command palette"),
        ("j k, arrows", "move in the list or the values"),
        ("h l", "move between the list and the values"),
        ("e, enter", "edit the selected value"),
        ("/", "filter the values; esc clears the filter"),
        ("a", "dependencies or affected modules"),
        ("m", "move the module's sources to the flat layout"),
        ("n", "create a module"),
        ("d", "add an LVM library to the module"),
        ("f, F", "fix the selected finding, or all of them"),
        ("o", "open the file in $EDITOR"),
        ("c", "run a Gradle command"),
        ("v", "log, tests, detekt or history of the Tasks tab"),
        ("n N", "next or previous error in the log"),
        ("t, a", "run the selected test, or the failed ones, again"),
        ("x", "cancel the running build"),
        ("u", "upgrade LVM to the selected release"),
        ("ctrl-c", "cancel the running build, or quit"),
        ("q", "quit"),
    ]
    .into_iter()
    .map(|(key, text)| Line::from(vec![Span::raw(format!("{key:>12}  ")).bold(), Span::raw(text)]))
    .collect();
    let area = centered(frame.area(), 68, rows.len() as u16 + 2);
    frame.render_widget(Clear, area);
    frame.render_widget(Paragraph::new(rows).block(Block::bordered().title(" Keys ")), area);
}

fn draw_palette(frame: &mut Frame, app: &App) {
    let Some(palette) = &app.palette else { return };
    let matches = palette.matches();
    let area = centered(frame.area(), 64, matches.len().max(1) as u16 + 4);
    frame.render_widget(Clear, area);
    let block = Block::bordered().title(" Commands ");
    let inner = block.inner(area);
    frame.render_widget(block, area);
    let [query, list] = Layout::vertical([Constraint::Length(2), Constraint::Fill(1)]).areas(inner);
    frame.render_widget(Line::from(vec!["> ".bold(), Span::raw(palette.query.clone()), "_".slow_blink()]), query);
    let items: Vec<ListItem> = matches
        .iter()
        .map(|action: &Action| {
            ListItem::new(Line::from(vec![Span::raw(format!("{:<44}", action.label())), Span::raw(action.key()).dim()]))
        })
        .collect();
    let mut state = ListState::default().with_selected((!matches.is_empty()).then_some(palette.selected));
    frame.render_stateful_widget(List::new(items).highlight_style(Style::new().reversed()), list, &mut state);
}

/// A form in the middle of the screen, as `lvm-tui new` shows it.
pub fn draw_form(frame: &mut Frame, form: &crate::form::Form) {
    let area = centered(frame.area(), 90, form::height(form) + 2);
    form::draw(frame, area, form);
}

/// A change as a diff, as `lvm-tui new` asks to confirm it.
pub fn draw_changes(frame: &mut Frame, title: &str, preview: &[String], scroll: usize) {
    dialog::draw_confirm(frame, title, preview, scroll);
}

pub(crate) fn centered(area: Rect, width: u16, height: u16) -> Rect {
    let [row] = Layout::vertical([Constraint::Length(height)]).flex(Flex::Center).areas(area);
    let [cell] = Layout::horizontal([Constraint::Length(width.min(area.width))]).flex(Flex::Center).areas(row);
    cell
}

/// A value as `lvm { }` would write it: lists joined with commas, `-` for none.
pub fn format_value(value: Option<&Value>) -> String {
    match value {
        None | Some(Value::Null) => "-".to_owned(),
        Some(Value::String(text)) if text.is_empty() => "\"\"".to_owned(),
        Some(Value::String(text)) => text.clone(),
        Some(Value::Array(items)) if items.is_empty() => "[]".to_owned(),
        Some(Value::Array(items)) => items.iter().map(|item| format_value(Some(item))).collect::<Vec<_>>().join(", "),
        Some(other) => other.to_string(),
    }
}

pub fn source_style(source: Source) -> Style {
    match source {
        Source::Default => Style::new().fg(Color::DarkGray),
        Source::Settings => Style::new().fg(Color::Blue),
        Source::Property => Style::new().fg(Color::Magenta),
        Source::Module => Style::new().fg(Color::Yellow).add_modifier(Modifier::BOLD),
    }
}

pub fn origin_label(origin: Origin) -> Span<'static> {
    match origin {
        Origin::Report => "lvmReport".green(),
        Origin::Files => "build files".yellow(),
    }
}

pub fn support_span(support: Support) -> Span<'static> {
    match support {
        Support::Supported => "ok".green(),
        Support::TooOld => "too old".red().bold(),
        Support::NewerThanTested => "newer than tested".yellow(),
        Support::Unknown => "unknown".dim(),
    }
}

/// `tech.annexflow.lvm.android.application` -> `android.application`.
pub fn short_plugin(id: &str) -> &str {
    id.strip_prefix("tech.annexflow.lvm.").unwrap_or(id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_values_as_a_script_would_set_them() {
        assert_eq!(format_value(None), "-");
        assert_eq!(format_value(Some(&serde_json::json!([]))), "[]");
        assert_eq!(format_value(Some(&serde_json::json!(["a", "b"]))), "a, b");
        assert_eq!(format_value(Some(&serde_json::json!(""))), "\"\"");
        assert_eq!(format_value(Some(&serde_json::json!(26))), "26");
    }
}
