//! The Library tab: published modules on the left; their POM, signing and ABI on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Cell, List, ListItem, Paragraph, Row, Table};

use super::source_style;
use crate::app::App;
use crate::library::{self, AbiState};
use crate::library_tab::LibraryFocus;

fn block(title: String, focused: bool) -> Block<'static> {
    let block = Block::bordered().title(title);
    if focused { block.border_style(Style::new().cyan()) } else { block }
}

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let width = app.published_modules().iter().map(|m| m.path.len()).max().unwrap_or(10) as u16 + 6;
    let [list, detail] = Layout::horizontal([Constraint::Length(width.clamp(18, 40)), Constraint::Fill(1)]).areas(area);
    let items: Vec<ListItem> = app.published_modules().iter().map(|m| ListItem::new(m.path.clone())).collect();
    let modules = List::new(items)
        .block(block(" Published ".to_owned(), app.library.focus == LibraryFocus::Modules))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(modules, list, &mut app.library.list);

    let Some(module) = app.selected_library().cloned() else { return };
    let [top, abi] = Layout::vertical([Constraint::Percentage(50), Constraint::Fill(1)]).areas(detail);
    let [pom_area, signing_area] = Layout::horizontal([Constraint::Fill(3), Constraint::Fill(2)]).areas(top);

    let (coordinates, release) = match &module.publication {
        Some(p) => (format!("{}:{}:{}", p.group, p.artifact_id, p.version), !p.version.ends_with("-SNAPSHOT")),
        None => (format!("{} (run r for its coordinates)", module.path), false),
    };
    let rows: Vec<Row> = library::pom(&module)
        .into_iter()
        .map(|(key, value, source)| {
            let required = library::REQUIRED_POM.contains(&key.as_str());
            let value = match value {
                Some(value) => Span::raw(value),
                None if required => "missing".red().bold(),
                None => "-".dim(),
            };
            Row::new(vec![
                Cell::from(key.trim_start_matches("publish.").to_owned()),
                Cell::from(value),
                Cell::from(Span::styled(source.label(), source_style(source))),
            ])
        })
        .collect();
    let title = format!(" {coordinates} ");
    let table = Table::new(rows, [Constraint::Length(26), Constraint::Fill(1), Constraint::Length(9)])
        .header(Row::new(["POM", "Value", "Source"]).style(Style::new().bold()))
        .block(block(title, app.library.focus == LibraryFocus::Pom).title_bottom(" e edit "))
        .row_highlight_style(Style::new().reversed());
    frame.render_stateful_widget(table, pom_area, &mut app.library.pom);

    let mut lines: Vec<Line> = app
        .library
        .secrets
        .iter()
        .map(|(key, places)| {
            let found = if places.is_empty() {
                "not set".dim()
            } else {
                let text = places.iter().map(|p| p.label()).collect::<Vec<_>>().join(", ");
                if places.contains(&library::Place::BuildProperties) {
                    Span::raw(text).red()
                } else {
                    Span::raw(text).green()
                }
            };
            Line::from(vec![Span::raw(format!("{key:<28}")), found])
        })
        .collect();
    let has = |key: &str| app.library.secrets.iter().any(|(k, places)| *k == key && !places.is_empty());
    lines.push(Line::from(""));
    lines.push(match (release, has("signingInMemoryKey")) {
        (false, _) => Line::from("A SNAPSHOT: no key needed to publish.".dim()),
        (true, true) => Line::from("A release: signed with the in-memory key.".green()),
        (true, false) => Line::from("A release needs signingInMemoryKey to publish.".yellow()),
    });
    if let Some(files) = &app.library.published {
        lines.push(Line::from(format!("Maven local has {} file(s) of it after the dry run.", files.len()).green()));
    }
    frame.render_widget(Paragraph::new(lines).block(Block::bordered().title(" Signing and upload ")), signing_area);

    draw_abi(frame, app, abi);
}

fn draw_abi(frame: &mut Frame, app: &App, area: Rect) {
    let focused = app.library.focus == LibraryFocus::Abi;
    let keys = " c check   u update the dump   p publish to Maven local ";
    let (title, lines): (Line, Vec<String>) = match &app.library.abi {
        AbiState::NotChecked => (Line::from(" ABI: not checked yet "), vec!["c runs checkKotlinAbi.".to_owned()]),
        AbiState::Matches { .. } if !app.library.uncommitted.is_empty() => (
            Line::from(vec![" ABI: ".into(), "api/ was updated and not committed".yellow(), " ".into()]),
            app.library.uncommitted.clone(),
        ),
        AbiState::Matches { .. } => (Line::from(vec![" ABI: ".into(), "matches api/".green(), " ".into()]), Vec::new()),
        AbiState::Changed(diffs) => {
            let removed: usize = diffs.iter().map(|d| d.removed).sum();
            let added: usize = diffs.iter().map(|d| d.added).sum();
            let mut spans = vec![Span::raw(" ABI changed: ")];
            if removed > 0 {
                spans.push(format!("{removed} line(s) removed, which may break consumers").red().bold());
                spans.push(Span::raw(", "));
            }
            spans.push(format!("{added} added ").green());
            (Line::from(spans), diffs.iter().flat_map(|d| d.diff.clone()).collect())
        }
    };
    let height = area.height.saturating_sub(2) as usize;
    let shown: Vec<Line> = lines
        .iter()
        .skip(app.library.abi_scroll.min(lines.len().saturating_sub(1)))
        .take(height)
        .map(|line| {
            // Kotlin indents dumps with tabs, which the terminal would show as nothing.
            let span = Span::raw(line.replace('\t', "    "));
            Line::from(if line.starts_with("+++") || line.starts_with("---") {
                span.bold()
            } else if line.starts_with('+') {
                span.green()
            } else if line.starts_with('-') {
                span.red()
            } else if line.starts_with("@@") {
                span.cyan()
            } else {
                span
            })
        })
        .collect();
    frame.render_widget(
        Paragraph::new(shown).block(block(String::new(), focused).title(title).title_bottom(keys)),
        area,
    );
}
