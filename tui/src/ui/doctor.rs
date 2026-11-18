//! The Doctor tab: what would fail LVM's plugins or the build, with a fix where lvm-tui has one.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, List, ListItem, Paragraph, Wrap};

use crate::app::App;
use crate::doctor::{Finding, Severity};

pub fn severity_span(severity: Severity) -> Span<'static> {
    match severity {
        Severity::Error => "error  ".red().bold(),
        Severity::Warning => "warning".yellow(),
        Severity::Info => "info   ".blue(),
    }
}

pub fn place(finding: &Finding) -> String {
    match (&finding.file, finding.line) {
        (Some(file), Some(line)) => format!("{}:{line}", file.display()),
        (Some(file), None) => file.display().to_string(),
        (None, _) => finding.module.clone().unwrap_or_default(),
    }
}

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    if app.env.is_none() {
        let text = Paragraph::new("Checking the machine: JDK, Android SDK, Xcode...".dim())
            .block(Block::bordered().title(" Doctor "));
        frame.render_widget(text, area);
        return;
    }
    if app.findings.is_empty() {
        let text = Paragraph::new(Line::from(vec!["No problems found. ".green(), "R reads the build again.".dim()]))
            .block(Block::bordered().title(" Doctor "));
        frame.render_widget(text, area);
        return;
    }
    let [list, detail] = Layout::vertical([Constraint::Fill(1), Constraint::Length(9)]).areas(area);
    let items: Vec<ListItem> = app
        .findings
        .iter()
        .map(|finding| {
            let fix = if finding.fix.is_some() { " fix".green() } else { "    ".into() };
            ListItem::new(Line::from(vec![
                severity_span(finding.severity),
                Span::raw(format!(" {:<24}", finding.rule)).bold(),
                fix,
                Span::raw(format!("  {}", place(finding))).dim(),
            ]))
        })
        .collect();
    let errors = app.findings.iter().filter(|f| f.severity == Severity::Error).count();
    let title = format!(" Doctor: {errors} error(s) of {} finding(s) ", app.findings.len());
    let list_widget = List::new(items).block(Block::bordered().title(title)).highlight_style(Style::new().reversed());
    frame.render_stateful_widget(list_widget, list, &mut app.doctor);

    let Some(finding) = app.selected_finding() else { return };
    let mut lines = vec![
        Line::from(vec![
            severity_span(finding.severity),
            Span::raw(format!(" [{}]  ", finding.rule)),
            Span::raw(place(finding)).dim(),
        ]),
        Line::from(""),
        Line::from(finding.message.clone()),
        Line::from(""),
    ];
    let keys = match &finding.fix {
        Some(fix) => Line::from(vec![
            "f ".bold(),
            Span::raw(format!("{}   ", fix.label())),
            "F ".bold(),
            "fix all   ".into(),
            "o ".bold(),
            "open in $EDITOR".into(),
        ]),
        None => Line::from(vec!["No automatic fix.   ".dim(), "o ".bold(), "open in $EDITOR".into()]),
    };
    lines.push(keys);
    frame.render_widget(Paragraph::new(lines).wrap(Wrap { trim: false }).block(Block::bordered()), detail);
}
