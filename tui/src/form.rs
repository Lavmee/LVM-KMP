//! A form of a few fields: text, one choice of several, toggles, and a list to pick from with a filter.

use ratatui::crossterm::event::{KeyCode, KeyEvent, KeyModifiers};

pub struct Form {
    pub title: String,
    pub fields: Vec<Field>,
    pub focus: usize,
    pub error: Option<String>,
}

pub struct Field {
    pub name: &'static str,
    pub label: &'static str,
    pub hint: String,
    pub kind: FieldKind,
    pub visible: bool,
}

pub enum FieldKind {
    Text(String),
    Choice { options: Vec<String>, selected: usize },
    Toggles { options: Vec<String>, on: Vec<bool>, cursor: usize },
    Pick { options: Vec<String>, filter: String, selected: usize },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FormEvent {
    None,
    Changed,
    Submit,
    Cancel,
}

impl Field {
    pub fn text(name: &'static str, label: &'static str, value: impl Into<String>, hint: impl Into<String>) -> Self {
        Self { name, label, hint: hint.into(), kind: FieldKind::Text(value.into()), visible: true }
    }

    pub fn choice(name: &'static str, label: &'static str, options: Vec<String>, selected: usize) -> Self {
        Self { name, label, hint: String::new(), kind: FieldKind::Choice { options, selected }, visible: true }
    }

    pub fn toggles(name: &'static str, label: &'static str, options: &[(&str, bool)]) -> Self {
        let kind = FieldKind::Toggles {
            options: options.iter().map(|(name, _)| (*name).to_owned()).collect(),
            on: options.iter().map(|(_, on)| *on).collect(),
            cursor: 0,
        };
        Self { name, label, hint: String::new(), kind, visible: true }
    }

    pub fn pick(name: &'static str, label: &'static str, options: Vec<String>) -> Self {
        let kind = FieldKind::Pick { options, filter: String::new(), selected: 0 };
        Self { name, label, hint: "type to filter".into(), kind, visible: true }
    }

    pub fn with_hint(mut self, hint: impl Into<String>) -> Self {
        self.hint = hint.into();
        self
    }
}

/// The options of a pick list that its filter keeps.
pub fn filtered<'a>(options: &'a [String], filter: &str) -> Vec<&'a String> {
    let filter = filter.to_lowercase();
    options.iter().filter(|option| option.to_lowercase().contains(&filter)).collect()
}

impl Form {
    pub fn new(title: impl Into<String>, fields: Vec<Field>) -> Self {
        Self { title: title.into(), fields, focus: 0, error: None }
    }

    pub fn key(&mut self, key: KeyEvent) -> FormEvent {
        let ctrl = key.modifiers.contains(KeyModifiers::CONTROL);
        // Up and down move in a pick list, and between fields everywhere else.
        let pick = matches!(self.fields[self.focus].kind, FieldKind::Pick { .. });
        match key.code {
            KeyCode::Esc => return FormEvent::Cancel,
            KeyCode::Enter => return FormEvent::Submit,
            KeyCode::Tab => return self.move_focus(1),
            KeyCode::BackTab => return self.move_focus(-1),
            KeyCode::Down if !pick => return self.move_focus(1),
            KeyCode::Up if !pick => return self.move_focus(-1),
            _ => {}
        }
        let field = &mut self.fields[self.focus];
        let event = match (&mut field.kind, key.code) {
            (FieldKind::Pick { options, filter, selected }, KeyCode::Down) => {
                let count = filtered(options, filter).len();
                *selected = (*selected + 1).min(count.saturating_sub(1));
                FormEvent::Changed
            }
            (FieldKind::Pick { selected, .. }, KeyCode::Up) => {
                *selected = selected.saturating_sub(1);
                FormEvent::Changed
            }
            (FieldKind::Text(text), KeyCode::Backspace) => {
                text.pop();
                FormEvent::Changed
            }
            (FieldKind::Text(text), KeyCode::Char(c)) if !ctrl => {
                text.push(c);
                FormEvent::Changed
            }
            (FieldKind::Pick { filter, selected, .. }, KeyCode::Backspace) => {
                filter.pop();
                *selected = 0;
                FormEvent::Changed
            }
            (FieldKind::Pick { filter, selected, .. }, KeyCode::Char(c)) if !ctrl => {
                filter.push(c);
                *selected = 0;
                FormEvent::Changed
            }
            (FieldKind::Choice { options, selected }, KeyCode::Right) => {
                *selected = (*selected + 1) % options.len().max(1);
                FormEvent::Changed
            }
            (FieldKind::Choice { options, selected }, KeyCode::Left) => {
                *selected = (*selected + options.len().max(1) - 1) % options.len().max(1);
                FormEvent::Changed
            }
            (FieldKind::Toggles { options, cursor, .. }, KeyCode::Right) => {
                *cursor = (*cursor + 1).min(options.len().saturating_sub(1));
                FormEvent::None
            }
            (FieldKind::Toggles { cursor, .. }, KeyCode::Left) => {
                *cursor = cursor.saturating_sub(1);
                FormEvent::None
            }
            (FieldKind::Toggles { on, cursor, .. }, KeyCode::Char(' ')) => {
                if let Some(value) = on.get_mut(*cursor) {
                    *value = !*value;
                }
                FormEvent::Changed
            }
            _ => FormEvent::None,
        };
        if event == FormEvent::Changed {
            self.error = None;
        }
        event
    }

    fn move_focus(&mut self, step: isize) -> FormEvent {
        let count = self.fields.len() as isize;
        let mut index = self.focus as isize;
        for _ in 0..count {
            index = (index + step).rem_euclid(count);
            if self.fields[index as usize].visible {
                self.focus = index as usize;
                break;
            }
        }
        FormEvent::None
    }

    fn field(&self, name: &str) -> &Field {
        self.fields.iter().find(|f| f.name == name).unwrap_or_else(|| panic!("no field {name}"))
    }

    pub fn text(&self, name: &str) -> String {
        match &self.field(name).kind {
            FieldKind::Text(text) => text.trim().to_owned(),
            _ => String::new(),
        }
    }

    pub fn choice(&self, name: &str) -> usize {
        match &self.field(name).kind {
            FieldKind::Choice { selected, .. } => *selected,
            _ => 0,
        }
    }

    pub fn chosen(&self, name: &str) -> String {
        match &self.field(name).kind {
            FieldKind::Choice { options, selected } => options.get(*selected).cloned().unwrap_or_default(),
            _ => String::new(),
        }
    }

    pub fn toggled(&self, name: &str) -> Vec<String> {
        match &self.field(name).kind {
            FieldKind::Toggles { options, on, .. } => {
                options.iter().zip(on).filter(|(_, on)| **on).map(|(option, _)| option.clone()).collect()
            }
            _ => Vec::new(),
        }
    }

    pub fn picked(&self, name: &str) -> Option<String> {
        match &self.field(name).kind {
            FieldKind::Pick { options, filter, selected } => {
                filtered(options, filter).get(*selected).map(|s| (*s).clone())
            }
            _ => None,
        }
    }

    pub fn set_visible(&mut self, name: &str, visible: bool) {
        if let Some(field) = self.fields.iter_mut().find(|f| f.name == name) {
            field.visible = visible;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn press(form: &mut Form, code: KeyCode) -> FormEvent {
        form.key(KeyEvent::new(code, KeyModifiers::NONE))
    }

    fn form() -> Form {
        Form::new(
            "Test",
            vec![
                Field::text("path", "Path", ":a", ""),
                Field::choice("kind", "Kind", vec!["one".into(), "two".into()], 0),
                Field::toggles("targets", "Targets", &[("android", true), ("ios", false)]),
                Field::pick("library", "Library", vec!["lvm-core".into(), "lvm-io".into(), "lvm-logger".into()]),
            ],
        )
    }

    #[test]
    fn edits_each_kind_of_field() {
        let mut form = form();
        press(&mut form, KeyCode::Char('b'));
        assert_eq!(form.text("path"), ":ab");
        press(&mut form, KeyCode::Tab);
        press(&mut form, KeyCode::Left);
        assert_eq!(form.chosen("kind"), "two");
        press(&mut form, KeyCode::Down);
        press(&mut form, KeyCode::Right);
        press(&mut form, KeyCode::Char(' '));
        press(&mut form, KeyCode::Left);
        press(&mut form, KeyCode::Char(' '));
        assert_eq!(form.toggled("targets"), ["ios"]);
        press(&mut form, KeyCode::Tab);
        press(&mut form, KeyCode::Char('l'));
        press(&mut form, KeyCode::Char('o'));
        press(&mut form, KeyCode::Down);
        assert_eq!(form.picked("library").as_deref(), Some("lvm-logger"));
        assert_eq!(press(&mut form, KeyCode::Enter), FormEvent::Submit);
    }

    #[test]
    fn skips_hidden_fields() {
        let mut form = form();
        form.set_visible("kind", false);
        press(&mut form, KeyCode::Tab);
        assert_eq!(form.focus, 2);
        press(&mut form, KeyCode::BackTab);
        assert_eq!(form.focus, 0);
    }
}
