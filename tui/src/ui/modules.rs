//! The Modules tab: the modules on the left; the selected module's setup, values and neighbors on the right.

use ratatui::Frame;
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Cell, List, ListItem, Paragraph, Row, Table};

use super::{format_value, origin_label, short_plugin, source_style};
use crate::app::{App, Focus, GraphPane};
use crate::project::model::Module;

pub fn draw(frame: &mut Frame, app: &mut App, area: Rect) {
    let [list, detail] = Layout::horizontal([Constraint::Length(list_width(app)), Constraint::Fill(1)]).areas(area);
    draw_list(frame, app, list);
    let Some(module) = app.selected_module().cloned() else {
        frame.render_widget(
            Paragraph::new("No modules: settings.gradle.kts includes none.").block(Block::bordered()),
            detail,
        );
        return;
    };
    let [info, values, graph] =
        Layout::vertical([Constraint::Length(8), Constraint::Fill(1), Constraint::Length(9)]).areas(detail);
    draw_info(frame, &module, info);
    draw_values(frame, app, values);
    draw_graph(frame, app, &module, graph);
}

fn list_width(app: &App) -> u16 {
    let longest = app.project.modules.iter().map(|m| m.path.len()).max().unwrap_or(10);
    (longest as u16 + 6).clamp(18, 44)
}

fn focused_block(title: String, focused: bool) -> Block<'static> {
    let block = Block::bordered().title(title);
    if focused { block.border_style(Style::new().cyan()) } else { block }
}

fn draw_list(frame: &mut Frame, app: &mut App, area: Rect) {
    let items: Vec<ListItem> = app
        .project
        .modules
        .iter()
        .map(|module| {
            let path = Span::raw(module.path.clone());
            ListItem::new(Line::from(if module.is_lvm() { path } else { path.dim() }))
        })
        .collect();
    let focused = app.modules.focus == Focus::List;
    let list = List::new(items)
        .block(focused_block(" Modules ".to_owned(), focused))
        .highlight_style(Style::new().reversed())
        .highlight_symbol("> ");
    frame.render_stateful_widget(list, area, &mut app.modules.list);
}

fn draw_info(frame: &mut Frame, module: &Module, area: Rect) {
    let label = |text: &'static str| Span::raw(format!("{text:<10}")).dim();
    let plugins = if module.plugins.is_empty() {
        "none".dim()
    } else {
        Span::raw(module.plugins.iter().map(|id| short_plugin(id)).collect::<Vec<_>>().join(", "))
    };
    let mut targets = module.targets.join(", ");
    if let Some(framework) = &module.framework {
        targets.push_str(&format!("  (framework {framework})"));
    }
    let layout = format!("{:?}", module.layout).to_lowercase();
    let mut lines = vec![
        Line::from(vec![
            label("Directory"),
            Span::raw(if module.dir.is_empty() { ".".to_owned() } else { module.dir.clone() }),
        ]),
        Line::from(vec![label("Plugins"), plugins]),
        Line::from(vec![label("Targets"), Span::raw(if targets.is_empty() { "-".to_owned() } else { targets })]),
        Line::from(vec![label("Layout"), Span::raw(layout)]),
        Line::from(vec![label("From"), origin_label(module.origin)]),
    ];
    if let Some(publication) = &module.publication {
        lines.push(Line::from(vec![
            label("Publishes"),
            Span::raw(format!("{}:{}:{}", publication.group, publication.artifact_id, publication.version)),
        ]));
    }
    frame.render_widget(Paragraph::new(lines).block(Block::bordered().title(format!(" {} ", module.path))), area);
}

fn draw_values(frame: &mut Frame, app: &mut App, area: Rect) {
    let rows: Vec<Row> = app
        .visible_values()
        .into_iter()
        .map(|row| {
            Row::new(vec![
                Cell::from(row.key.clone()),
                Cell::from(format_value(row.value.as_ref())),
                Cell::from(Span::styled(row.source.label(), source_style(row.source))),
            ])
        })
        .collect();
    let count = rows.len();
    let focused = app.modules.focus == Focus::Values;
    let filter = &app.modules.filter;
    let title = match (app.modules.filtering, filter.is_empty()) {
        (true, _) => format!(" Values  /{filter}_ "),
        (false, false) => format!(" Values  /{filter}  ({count}) "),
        (false, true) => format!(" Values ({count}) "),
    };
    let header = Row::new(["Key", "Value", "Source"]).style(Style::new().bold());
    let widths = [Constraint::Length(36), Constraint::Fill(1), Constraint::Length(9)];
    let table = Table::new(rows, widths)
        .header(header)
        .block(focused_block(title, focused))
        .row_highlight_style(Style::new().reversed());
    frame.render_stateful_widget(table, area, &mut app.modules.values);
}

fn draw_graph(frame: &mut Frame, app: &App, module: &Module, area: Rect) {
    let project = &app.project;
    match app.modules.pane {
        GraphPane::Dependencies => {
            let [uses, used_by] = Layout::horizontal([Constraint::Fill(1), Constraint::Fill(1)]).areas(area);
            let mut dependencies: Vec<Line> = module
                .project_dependencies
                .iter()
                .map(|edge| {
                    Line::from(vec![
                        Span::raw(edge.target.clone()),
                        Span::raw(format!("  {}", edge.configuration)).dim(),
                    ])
                })
                .collect();
            dependencies.extend(module.lvm_dependencies.iter().map(|edge| {
                Line::from(vec![
                    Span::raw(edge.target.clone()).cyan(),
                    Span::raw(format!("  {}", edge.configuration)).dim(),
                ])
            }));
            if dependencies.is_empty() {
                dependencies.push(Line::from("none".dim()));
            }
            let mut dependents: Vec<Line> = project.dependents(&module.path).into_iter().map(Line::from).collect();
            if dependents.is_empty() {
                dependents.push(Line::from("none".dim()));
            }
            frame.render_widget(Paragraph::new(dependencies).block(Block::bordered().title(" Depends on ")), uses);
            frame.render_widget(
                Paragraph::new(dependents).block(Block::bordered().title(" Used by  (a: affected) ")),
                used_by,
            );
        }
        GraphPane::Affected => {
            let affected = project.affected(&module.path);
            let lines: Vec<Line> = if affected.is_empty() {
                vec![Line::from("No other module depends on it.".dim())]
            } else {
                affected.iter().map(|path| Line::from(path.clone())).collect()
            };
            let title = format!(" Affected by a change to {}: {}  (a: dependencies) ", module.path, affected.len());
            frame.render_widget(Paragraph::new(lines).block(Block::bordered().title(title)), area);
        }
    }
}
