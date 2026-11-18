//! The Overview tab: which LVM the build uses, whether its reports are current, its tools and its modules.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Cell, Paragraph, Row, Table, Wrap};

use super::{origin_label, short_plugin, support_span};
use crate::app::App;
use crate::project::model::{Origin, REPORT_SINCE, ReportState};

pub fn draw(frame: &mut Frame, app: &App, area: Rect) {
    let project = &app.project;
    let [summary, toolchain, rest] = Layout::vertical([
        Constraint::Length(7),
        Constraint::Length(project.toolchain.len() as u16 + 3),
        Constraint::Fill(1),
    ])
    .areas(area);
    let [settings, modules] = Layout::horizontal([Constraint::Length(36), Constraint::Fill(1)]).areas(rest);

    draw_summary(frame, app, summary);
    draw_toolchain(frame, app, toolchain);
    draw_settings(frame, app, settings);
    draw_modules(frame, app, modules);
}

fn draw_summary(frame: &mut Frame, app: &App, area: Rect) {
    let project = &app.project;
    let lvm = match project.reports.modules.values().next() {
        Some(report) => Line::from(vec![Span::raw(report.lvm_version.clone()).bold(), "  from lvmReport".dim()]),
        None => Line::from(vec![Span::raw(project.lvm.label()).bold(), "  from settings.gradle.kts".dim()]),
    };
    let report = match &project.report {
        ReportState::Fresh => Line::from("current".green()),
        ReportState::Stale => {
            Line::from(vec!["older than the build scripts".yellow(), ": press r to run lvmReport".dim()])
        }
        ReportState::Missing => Line::from(vec!["none yet".yellow(), ": press r to run lvmReport".dim()]),
        ReportState::Unsupported { version } => Line::from(vec![
            format!("needs LVM {REPORT_SINCE}+").yellow(),
            format!(": LVM {version} has no lvmReport, so values come from the build files and may be missing").dim(),
        ]),
    };
    let mut problems = Vec::new();
    if let Some(schema) = project.reports.newer_schema {
        problems.push(Line::from(
            format!("Reports use format {schema}, newer than this lvm-tui knows: update lvm-tui").yellow(),
        ));
    }
    if !project.reports.errors.is_empty() {
        problems.push(Line::from(format!("{} report(s) could not be read", project.reports.errors.len()).red()));
    }
    if project.settings.as_ref().is_some_and(|s| s.groovy) {
        problems.push(Line::from("settings.gradle uses the Groovy DSL, which lvm-tui reads only partly".yellow()));
    }
    let label = |text: &'static str| Span::raw(format!("{text:<10}")).dim();
    let mut lines = vec![
        Line::from(vec![label("Root"), Span::raw(project.root.display().to_string())]),
        Line::from([vec![label("LVM")], lvm.spans].concat()),
        Line::from([vec![label("Reports")], report.spans].concat()),
        Line::from([vec![label("Doctor")], doctor_summary(app)].concat()),
    ];
    lines.extend(problems);
    frame
        .render_widget(Paragraph::new(lines).wrap(Wrap { trim: true }).block(Block::bordered().title(" Build ")), area);
}

fn draw_toolchain(frame: &mut Frame, app: &App, area: Rect) {
    let rows = app.project.toolchain.iter().map(|tool| {
        let name = match tool.name.as_str() {
            "gradle" => "Gradle",
            "kotlin" => "Kotlin",
            "agp" => "AGP",
            other => other,
        };
        let text = |value: &Option<String>| Cell::from(value.clone().unwrap_or_else(|| "-".to_owned()));
        Row::new(vec![
            Cell::from(name),
            text(&tool.actual),
            text(&tool.minimum),
            text(&tool.tested),
            Cell::from(support_span(tool.support())),
            Cell::from(origin_label(tool.origin)),
        ])
    });
    let header = Row::new(["Tool", "Used", "Minimum", "Tested", "Status", "From"]).style(Style::new().bold());
    let widths = [
        Constraint::Length(8),
        Constraint::Length(12),
        Constraint::Length(10),
        Constraint::Length(10),
        Constraint::Length(19),
        Constraint::Fill(1),
    ];
    frame.render_widget(Table::new(rows, widths).header(header).block(Block::bordered().title(" Toolchain ")), area);
}

fn draw_settings(frame: &mut Frame, app: &App, area: Rect) {
    let options = &app.project.options;
    let flag = |value: Option<bool>| value.map_or_else(|| "-".to_owned(), |v| v.to_string());
    let label = |text: &'static str| Span::raw(format!("{text:<16}")).dim();
    let lines = vec![
        Line::from(vec![label("catalog"), Span::raw(flag(options.catalog))]),
        Line::from(vec![label("catalogName"), Span::raw(options.catalog_name.clone())]),
        Line::from(vec![
            label("catalogVersion"),
            Span::raw(options.catalog_version.clone().unwrap_or_else(|| "-".to_owned())),
        ]),
        Line::from(vec![label("repositories"), Span::raw(flag(options.repositories))]),
        Line::from(""),
        Line::from(vec![label("from"), origin_label(options.origin)]),
    ];
    frame.render_widget(Paragraph::new(lines).block(Block::bordered().title(" Settings plugin ")), area);
}

fn draw_modules(frame: &mut Frame, app: &App, area: Rect) {
    let modules = &app.project.modules;
    let rows = modules.iter().map(|module| {
        let plugins = module.plugins.iter().map(|id| short_plugin(id)).collect::<Vec<_>>().join(" ");
        let path = if module.is_lvm() { Span::raw(module.path.clone()) } else { Span::raw(module.path.clone()).dim() };
        let from = match module.origin {
            Origin::Report => "".into(),
            Origin::Files if module.is_lvm() => "files".yellow(),
            Origin::Files => "".into(),
        };
        Row::new(vec![Cell::from(path), Cell::from(plugins), Cell::from(module.targets.join(" ")), Cell::from(from)])
    });
    let lvm = modules.iter().filter(|m| m.is_lvm()).count();
    let title = format!(" Modules: {} with LVM of {} ", lvm, modules.len());
    let header = Row::new(["Path", "LVM plugins", "Targets", ""]).style(Style::new().bold());
    let widths =
        [Constraint::Percentage(30), Constraint::Percentage(45), Constraint::Percentage(18), Constraint::Length(5)];
    frame.render_widget(Table::new(rows, widths).header(header).block(Block::bordered().title(title)), area);
}

fn doctor_summary(app: &App) -> Vec<Span<'static>> {
    if app.env.is_none() {
        return vec!["checking the machine...".dim()];
    }
    let count = |severity| app.findings.iter().filter(|f| f.severity == severity).count();
    let (errors, warnings) = (count(crate::doctor::Severity::Error), count(crate::doctor::Severity::Warning));
    if errors + warnings == 0 {
        return vec!["no problems".green()];
    }
    let mut spans = Vec::new();
    if errors > 0 {
        spans.push(format!("{errors} error(s)").red().bold());
        spans.push(Span::raw("  "));
    }
    if warnings > 0 {
        spans.push(format!("{warnings} warning(s)").yellow());
    }
    spans.push(": press 3".dim());
    spans
}
