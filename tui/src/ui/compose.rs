//! The Compose tab: modules with Compose on the left; the compiler's metrics and findings on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Cell, List, ListItem, Paragraph, Row, Table, Wrap};

use crate::app::App;
use crate::compose::{self, Finding, Stability};
use crate::compose_tab::ComposeFocus;

fn block(title: String, focused: bool) -> Block<'static> {
    let block = Block::bordered().title(title);
    if focused { block.border_style(Style::new().cyan()) } else { block }
}

/// `com.example.Root.Child` -> `Root.Child`.
fn short(name: &str) -> &str {
    let package: usize = name.split('.').take_while(|s| s.starts_with(char::is_lowercase)).map(|s| s.len() + 1).sum();
    if package >= name.len() { name.rsplit('.').next().unwrap_or(name) } else { &name[package..] }
}

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let width = app.compose_modules().iter().map(|m| m.path.len()).max().unwrap_or(10) as u16 + 6;
    let [list, detail] = Layout::horizontal([Constraint::Length(width.clamp(18, 40)), Constraint::Fill(1)]).areas(area);
    let items: Vec<ListItem> = app.compose_modules().iter().map(|m| ListItem::new(m.path.clone())).collect();
    let modules = List::new(items)
        .block(block(" Compose ".to_owned(), app.compose.focus == ComposeFocus::Modules))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(modules, list, &mut app.compose.list);

    let Some(module) = app.selected_compose_module().cloned() else { return };
    let report = &app.compose.report;
    let metrics_height = if report.metrics.is_empty() { 3 } else { report.metrics.len() as u16 + 3 };
    let [metrics_area, findings_area] =
        Layout::vertical([Constraint::Length(metrics_height), Constraint::Fill(1)]).areas(detail);

    let task = compose::compile_task(&module).unwrap_or_else(|| "its compile task".to_owned());
    if report.is_empty() {
        let lines = vec![
            Line::from(format!("No reports yet: c compiles {task} with the Compose compiler's reports on.")),
            Line::from(
                "The build's files stay as they are: Gradle properties turn the reports on for this build.".dim(),
            ),
        ];
        frame.render_widget(
            Paragraph::new(lines).wrap(Wrap { trim: true }).block(block(format!(" {} ", module.path), false)),
            detail,
        );
        return;
    }

    let title = format!(" {} metrics ", module.path);
    if report.metrics.is_empty() {
        let text = Line::from("No metrics: c writes them with the reports.".dim());
        frame.render_widget(Paragraph::new(text).block(block(title, false)), metrics_area);
    } else {
        let count =
            |n: u64, warn: bool| if warn { Cell::from(n.to_string().yellow()) } else { Cell::from(n.to_string()) };
        let rows: Vec<Row> = report
            .metrics
            .iter()
            .map(|m| {
                Row::new(vec![
                    Cell::from(m.compilation.clone()),
                    Cell::from(m.composables.to_string()),
                    Cell::from(m.restartable.to_string()),
                    count(m.skippable, m.skippable < m.restartable && !m.strong_skipping),
                    Cell::from(m.classes.to_string()),
                    count(m.unstable_classes, m.unstable_classes > 0),
                    Cell::from(m.uncertain_classes.to_string()),
                    Cell::from(if m.strong_skipping { "on" } else { "off" }),
                ])
            })
            .collect();
        let header = [
            "Compilation",
            "Composables",
            "Restartable",
            "Skippable",
            "Classes",
            "Unstable",
            "Uncertain",
            "Strong skipping",
        ];
        let widths = [14, 12, 12, 10, 8, 9, 10, 15].map(Constraint::Length);
        let table =
            Table::new(rows, widths).header(Row::new(header).style(Style::new().bold())).block(block(title, false));
        frame.render_widget(table, metrics_area);
    }

    let findings = app.compose_findings();
    let focused = app.compose.focus == ComposeFocus::Findings;
    let keys = format!(" c compile {task} again   enter open the declaration ");
    let title = format!(" Findings: {} ", findings.len());
    if findings.is_empty() {
        let text = Line::from("Every class is stable, and every composable skips with stable arguments.".green());
        frame.render_widget(Paragraph::new(text).block(block(title, focused).title_bottom(keys)), findings_area);
        return;
    }
    let items: Vec<ListItem> = findings.iter().map(finding_item).collect();
    let list = List::new(items)
        .block(block(title, focused).title_bottom(keys))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list, findings_area, &mut app.compose.findings);
}

fn finding_item(finding: &Finding) -> ListItem<'static> {
    let (label, name, detail) = match finding {
        Finding::Class { name, stability, fields } => {
            let label = match stability {
                Stability::Unstable => "unstable class ".red(),
                _ => "uncertain class".yellow(),
            };
            (label, short(name).to_owned(), fields.join(", "))
        }
        Finding::NotSkippable { name } => ("doesn't skip   ".red(), format!("{}()", short(name)), String::new()),
        Finding::UnstableParams { name, params } => {
            let detail = format!("compared by instance: {}", params.join(", "));
            ("unstable args  ".yellow(), format!("{}()", short(name)), detail)
        }
    };
    let mut lines = vec![Line::from(vec![label, Span::raw("  "), Span::raw(name).bold()])];
    if !detail.is_empty() {
        lines.push(Line::from(format!("                 {detail}").dim()));
    }
    ListItem::new(lines)
}
