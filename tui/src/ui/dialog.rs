//! Windows over the tabs: a change to confirm as a diff, a value to edit, and the offer to build after a move.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout};
use ratatui::style::Stylize;
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Clear, Paragraph, Wrap};

use super::{centered, format_value, source_style};
use crate::app::{App, Dialog, EditDialog};
use crate::project::keys;

pub fn draw(frame: &mut Frame, app: &App) {
    match &app.dialog {
        Some(Dialog::Confirm { title, preview, scroll, .. }) => draw_confirm(frame, title, preview, *scroll),
        Some(Dialog::Edit(edit)) => draw_edit(frame, edit),
        Some(Dialog::OfferBuild) => {
            let area = centered(frame.area(), 64, 5);
            frame.render_widget(Clear, area);
            let lines = vec![
                Line::from("Sources moved. Run lvmReport and build to check the module?"),
                Line::from(""),
                Line::from(vec!["y ".bold(), "run   ".into(), "n ".bold(), "not now".into()]),
            ];
            frame.render_widget(Paragraph::new(lines).block(Block::bordered().title(" Build ")), area);
        }
        None => {}
    }
}

fn draw_confirm(frame: &mut Frame, title: &str, preview: &[String], scroll: usize) {
    let full = frame.area();
    let area = centered(full, full.width.saturating_sub(8).max(40), full.height.saturating_sub(4).max(10));
    frame.render_widget(Clear, area);
    let block = Block::bordered().title(format!(" {title} "));
    let inner = block.inner(area);
    frame.render_widget(block, area);
    let [diff, keys] = Layout::vertical([Constraint::Fill(1), Constraint::Length(1)]).areas(inner);
    let lines: Vec<Line> = preview
        .iter()
        .skip(scroll)
        .take(diff.height as usize)
        .map(|line| {
            let span = Span::raw(line.clone());
            Line::from(if line.starts_with("+++") || line.starts_with("---") {
                span.bold()
            } else if line.starts_with('+') {
                span.green()
            } else if line.starts_with('-') {
                span.red()
            } else if line.starts_with("@@") {
                span.cyan()
            } else if line.starts_with("move ") || line.starts_with("git ") {
                span.yellow()
            } else {
                span
            })
        })
        .collect();
    frame.render_widget(Paragraph::new(lines), diff);
    let position = format!("  {}/{}", (scroll + 1).min(preview.len()), preview.len());
    frame.render_widget(
        Line::from(vec![
            "y ".bold(),
            "apply   ".into(),
            "n ".bold(),
            "cancel   ".into(),
            "j k space ".bold(),
            "scroll".into(),
            position.dim(),
        ]),
        keys,
    );
}

fn draw_edit(frame: &mut Frame, edit: &EditDialog) {
    let area = centered(frame.area(), 76, 13);
    frame.render_widget(Clear, area);
    let kind = keys::find(&edit.key).map_or("string".to_owned(), |key| format!("{:?}", key.kind).to_lowercase());
    let hint = match keys::find(&edit.key).and_then(|key| key.module) {
        Some(keys::ListMode::Append) => "  comma-separated; a module's list is appended to the build-wide one",
        Some(keys::ListMode::Replace) => "  comma-separated; a module's list replaces the build-wide one",
        None => "",
    };
    let layers: Vec<Span> = edit
        .layers
        .iter()
        .enumerate()
        .flat_map(|(i, layer)| {
            let label = Span::raw(format!(" {} ", layer.label()));
            [if i == edit.layer { label.reversed() } else { label.dim() }, Span::raw(" ")]
        })
        .collect();
    let label = |text: &'static str| Span::raw(format!("{text:<8}")).dim();
    let mut lines = vec![
        Line::from(vec![label("Key"), Span::raw(edit.key.clone()).bold(), Span::raw(format!("  {kind}")).dim()]),
        Line::from(vec![
            label("Now"),
            Span::raw(format_value(edit.value.as_ref())),
            Span::raw("  from ").dim(),
            Span::styled(edit.source.label(), source_style(edit.source)),
            Span::raw(format!("  in {}", edit.module)).dim(),
        ]),
        Line::from(""),
        Line::from([vec![label("Write to")], layers].concat()),
        Line::from(vec![label("Value"), Span::raw(edit.input.clone()), "_".slow_blink()]),
        Line::from(hint.dim()),
        Line::from(""),
    ];
    if let Some(error) = &edit.error {
        lines.push(Line::from(error.clone().red()));
    } else {
        lines.push(Line::from(""));
    }
    lines.push(Line::from(vec![
        "enter ".bold(),
        "preview   ".into(),
        "tab ".bold(),
        "layer   ".into(),
        "ctrl-d ".bold(),
        "remove from layer   ".into(),
        "esc ".bold(),
        "cancel".into(),
    ]));
    frame.render_widget(
        Paragraph::new(lines).wrap(Wrap { trim: false }).block(Block::bordered().title(" Edit value ")),
        area,
    );
}
