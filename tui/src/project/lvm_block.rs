//! Values that an `lvm { }` block of a settings or module script sets, by their `lvm.*` key.

use std::ops::Range;

use serde::Serialize;
use serde_json::Value;

use super::kts::{Form, Literal, Script};

/// One write to a value, in script order.
#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct ScriptValue {
    /// The key without the `lvm.` prefix, such as `android.minSdk`.
    pub key: String,
    pub op: ValueOp,
    pub line: usize,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(tag = "op", content = "value", rename_all = "camelCase")]
pub enum ValueOp {
    /// `minSdk = 26`, `platforms.set(listOf(...))`
    Set(Value),
    /// `optIns.add("x")`, `optIns.addAll("a", "b")`
    Add(Vec<String>),
}

/// The values set by the statements of an `lvm { }` block, skipping [skip] at its top level, such as the settings
/// plugin's own options. `targets { }` is left to the caller.
pub fn read_values(script: &Script, body: Range<usize>, skip: &[&str]) -> Vec<ScriptValue> {
    let mut values = Vec::new();
    collect(script, body, &[], skip, &mut values);
    values
}

fn collect(script: &Script, body: Range<usize>, prefix: &[String], skip: &[&str], values: &mut Vec<ScriptValue>) {
    for statement in script.statements(body) {
        if statement.chain.is_empty() {
            continue;
        }
        if prefix.is_empty() && (skip.contains(&statement.chain[0].as_str()) || statement.chain[0] == "targets") {
            continue;
        }
        let path: Vec<String> = prefix.iter().cloned().chain(statement.chain.iter().cloned()).collect();
        let line = script.line(statement.tokens.start);
        match statement.form {
            Form::Block { body } => collect(script, body, &path, skip, values),
            Form::Assign { value } => {
                if let Some(literal) = script.literal(value) {
                    values.push(ScriptValue { key: path.join("."), op: ValueOp::Set(json(literal)), line });
                }
            }
            Form::Call { args, lambda, .. } => {
                let (receiver, method) = path.split_at(path.len() - 1);
                match method[0].as_str() {
                    "add" | "addAll" => {
                        if let Some(strings) = list_args(script, &args) {
                            values.push(ScriptValue { key: receiver.join("."), op: ValueOp::Add(strings), line });
                        }
                    }
                    "set" | "convention" if args.len() == 1 => {
                        if let Some(literal) = script.literal(args[0].clone()) {
                            values.push(ScriptValue { key: receiver.join("."), op: ValueOp::Set(json(literal)), line });
                        }
                    }
                    // Raw build-wide values of the settings plugin: values.put("android.minSdk", "26").
                    "put" if receiver == ["values"] => {
                        if let Some([key, value]) = script.string_args(&args).as_deref() {
                            values.push(ScriptValue {
                                key: key.clone(),
                                op: ValueOp::Set(Value::String(value.clone())),
                                line,
                            });
                        }
                    }
                    _ => {
                        if let Some(lambda) = lambda {
                            collect(script, lambda, &path, skip, values);
                        }
                    }
                }
            }
            Form::Other => {}
        }
    }
}

/// `"a", "b"` or a single `listOf("a", "b")`.
fn list_args(script: &Script, args: &[Range<usize>]) -> Option<Vec<String>> {
    if let [single] = args
        && let Some(Literal::List(items)) = script.literal(single.clone())
    {
        return Some(items);
    }
    script.string_args(args)
}

pub fn json(literal: Literal) -> Value {
    match literal {
        Literal::Str(value) => Value::String(value),
        Literal::Int(value) => Value::from(value),
        Literal::Bool(value) => Value::Bool(value),
        Literal::List(items) => Value::Array(items.into_iter().map(Value::String).collect()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn values(source: &str) -> Vec<(String, ValueOp)> {
        let script = Script::parse(source);
        let lvm = script
            .statements(script.all())
            .into_iter()
            .find_map(|s| match s.form {
                Form::Block { body } if s.chain == ["lvm"] => Some(body),
                _ => None,
            })
            .expect("lvm block");
        read_values(&script, lvm, &["catalog"]).into_iter().map(|v| (v.key, v.op)).collect()
    }

    #[test]
    fn reads_nested_sections_lists_and_raw_values() {
        let read = values(
            r#"lvm {
                catalog = false
                android { minSdk = 26 }
                kotlin {
                    optIns.add("a")
                    optIns.addAll("b", "c")
                    freeCompilerArgs.addAll(listOf("-X"))
                }
                publish { license { name = "MIT" } }
                mainContext { platforms.set(listOf("ios")) }
                values.put("jvm.target", "17")
                targets { jvm() }
            }"#,
        );
        assert_eq!(
            read,
            vec![
                ("android.minSdk".into(), ValueOp::Set(Value::from(26))),
                ("kotlin.optIns".into(), ValueOp::Add(vec!["a".into()])),
                ("kotlin.optIns".into(), ValueOp::Add(vec!["b".into(), "c".into()])),
                ("kotlin.freeCompilerArgs".into(), ValueOp::Add(vec!["-X".into()])),
                ("publish.license.name".into(), ValueOp::Set(Value::from("MIT"))),
                ("mainContext.platforms".into(), ValueOp::Set(serde_json::json!(["ios"]))),
                ("jvm.target".into(), ValueOp::Set(Value::from("17"))),
            ]
        );
    }

    #[test]
    fn skips_values_it_cannot_read_literally() {
        let read = values("lvm { android { minSdk = libs.versions.minSdk.get().toInt() } }");
        assert!(read.is_empty());
    }
}
