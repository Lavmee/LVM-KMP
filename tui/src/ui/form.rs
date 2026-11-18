//! Draws a form: a label and a value per field, the focused one's hint, and the form's error.

use ratatui::Frame;
use ratatui::layout::Rect;
use ratatui::style::Stylize;
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Clear, Paragraph, Wrap};

use crate::form::{FieldKind, Form, filtered};

/// The rows [form] takes, for sizing its window.
pub fn height(form: &Form) -> u16 {
    let rows: usize = form
        .fields
        .iter()
        .filter(|f| f.visible)
        .map(|field| match &field.kind {
            FieldKind::Pick { options, filter, .. } => 1 + filtered(options, filter).len().min(8),
            _ => 1,
        })
        .sum();
    rows as u16 + 6
}

pub fn draw(frame: &mut Frame, area: Rect, form: &Form) {
    frame.render_widget(Clear, area);
    let mut lines = Vec::new();
    for (index, field) in form.fields.iter().enumerate().filter(|(_, f)| f.visible) {
        let focused = index == form.focus;
        let label = Span::raw(format!("{:<16}", field.label));
        let label = if focused { label.cyan().bold() } else { label.dim() };
        match &field.kind {
            FieldKind::Text(text) => {
                let cursor = if focused { "_".slow_blink() } else { "".into() };
                lines.push(Line::from(vec![label, Span::raw(text.clone()), cursor]));
            }
            FieldKind::Choice { options, selected } => {
                let mut spans = vec![label];
                for (i, option) in options.iter().enumerate() {
                    let span = Span::raw(format!(" {option} "));
                    spans.push(if i == *selected { span.reversed() } else { span.dim() });
                }
                lines.push(Line::from(spans));
            }
            FieldKind::Toggles { options, on, cursor } => {
                let mut spans = vec![label];
                for (i, (option, on)) in options.iter().zip(on).enumerate() {
                    let mark = if *on { "[x]" } else { "[ ]" };
                    let span = Span::raw(format!("{mark} {option}  "));
                    spans.push(if focused && i == *cursor { span.reversed() } else { span });
                }
                lines.push(Line::from(spans));
            }
            FieldKind::Pick { options, filter, selected } => {
                let cursor = if focused { "_".slow_blink() } else { "".into() };
                lines.push(Line::from(vec![label, Span::raw(format!("/{filter}")), cursor]));
                let shown = filtered(options, filter);
                let start = selected.saturating_sub(7);
                for (i, option) in shown.iter().enumerate().skip(start).take(8) {
                    let span = Span::raw(format!("{:16}{option}", ""));
                    lines.push(Line::from(if i == *selected { span.reversed() } else { span }));
                }
                if shown.is_empty() {
                    lines.push(Line::from(format!("{:16}nothing matches", "").dim()));
                }
            }
        }
    }
    lines.push(Line::from(""));
    let hint = form.fields.get(form.focus).map(|f| f.hint.clone()).unwrap_or_default();
    lines.push(match &form.error {
        Some(error) => Line::from(error.clone().red()),
        None => Line::from(hint.dim()),
    });
    let keys = Line::from(vec![
        "enter ".bold(),
        "preview   ".into(),
        "tab ".bold(),
        "next field   ".into(),
        "esc ".bold(),
        "cancel".into(),
    ]);
    lines.push(keys);
    let block = Block::bordered().title(format!(" {} ", form.title));
    frame.render_widget(Paragraph::new(lines).wrap(Wrap { trim: false }).block(block), area);
}
