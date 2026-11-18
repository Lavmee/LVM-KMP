//! The Updates tab: releases on the left, what the selected one changes for the build on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Cell, List, ListItem, Paragraph, Row, Table, Wrap};

use crate::app::App;
use crate::upgrade;

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let current = upgrade::current_version(&app.project);
    let [list, detail] = Layout::horizontal([Constraint::Length(30), Constraint::Fill(1)]).areas(area);

    let versions = match &app.updates.versions {
        None if !app.fetch_releases => {
            frame.render_widget(
                Paragraph::new("Maven Central is off.".dim()).block(Block::bordered().title(" Releases ")),
                area,
            );
            return;
        }
        None => {
            let text = Paragraph::new("Asking Maven Central for LVM's releases...".dim());
            frame.render_widget(text.block(Block::bordered().title(" Releases ")), area);
            return;
        }
        Some(Err(error)) => {
            let text = Paragraph::new(vec![
                Line::from("Maven Central can't be reached, and no earlier answer is kept:".red()),
                Line::from(error.clone()),
                Line::from(""),
                Line::from("f asks again. lvm-tui upgrade --to <version> works without it.".dim()),
            ]);
            frame.render_widget(text.wrap(Wrap { trim: false }).block(Block::bordered().title(" Releases ")), area);
            return;
        }
        Some(Ok(versions)) => versions.clone(),
    };

    let latest = versions.first().cloned();
    let items: Vec<ListItem> = versions
        .iter()
        .map(|version| {
            let mut spans = vec![Span::raw(format!("{version:<12}"))];
            if Some(version) == current.as_ref() {
                spans.push("current ".cyan());
            }
            if Some(version) == latest.as_ref() {
                spans.push("latest".green());
            }
            ListItem::new(Line::from(spans))
        })
        .collect();
    let list_widget = List::new(items)
        .block(Block::bordered().title(" Releases ").title_bottom(" f ask again "))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list_widget, list, &mut app.updates.list);

    let Some(target) = app.updates.selected_version().map(str::to_owned) else { return };
    let title = format!(" {} -> {target} ", current.as_deref().unwrap_or("?"));
    let block = Block::bordered().title(title).title_bottom(" u upgrade: shows the change first ");
    let catalog = app.updates.catalogs.get(&target);
    let Some(Ok(target_catalog)) = catalog else {
        let text = match catalog {
            Some(Err(error)) => Line::from(format!("The catalog of {target} can't be read: {error}").red()),
            _ => Line::from(format!("Reading the catalog of {target}...").dim()),
        };
        frame.render_widget(Paragraph::new(text).block(block), detail);
        return;
    };
    let current_catalog = current.as_ref().and_then(|v| app.updates.catalogs.get(v)).and_then(|c| c.as_ref().ok());
    let (rows, notes) = upgrade::compare(&app.project, current_catalog, target_catalog, &target);

    let inner = block.inner(detail);
    frame.render_widget(block, detail);
    let [table, notes_area] =
        Layout::vertical([Constraint::Length(rows.len() as u16 + 2), Constraint::Fill(1)]).areas(inner);
    let show = |value: &Option<String>| value.clone().unwrap_or_else(|| "-".to_owned());
    let table_rows = rows.iter().map(|row| {
        let changed = row.current != row.target;
        let target = Span::raw(show(&row.target));
        Row::new(vec![
            Cell::from(row.name),
            Cell::from(show(&row.build)),
            Cell::from(show(&row.current)),
            Cell::from(if changed { target.yellow().bold() } else { target }),
        ])
    });
    let header = Row::new(vec![
        "Tested with".to_owned(),
        "The build".to_owned(),
        current.clone().unwrap_or_else(|| "current".to_owned()),
        target.clone(),
    ])
    .style(Style::new().bold());
    let widths = [Constraint::Length(24), Constraint::Length(12), Constraint::Length(16), Constraint::Fill(1)];
    frame.render_widget(Table::new(table_rows, widths).header(header), table);
    let lines: Vec<Line> = notes.into_iter().map(|note| Line::from(vec!["- ".dim(), Span::raw(note)])).collect();
    frame.render_widget(Paragraph::new(lines).wrap(Wrap { trim: false }), notes_area);
}
