//! What can run, and how: an Android app on a device, an iOS app from the build's Xcode project on a simulator, or a
//! JVM app with Gradle. Each run is one shell script, so a failed step stops the steps after it.

use std::path::{Path, PathBuf};

use crate::devices::{Device, Platform};
use crate::gradle::Program;
use crate::project::Project;
use crate::project::model::Module;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Runnable {
    pub module: String,
    pub platform: Platform,
    pub kind: RunKind,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RunKind {
    AndroidApp {
        application_id: String,
    },
    /// An iOS app of an Xcode project in the build, whose scheme is named after the project.
    IosApp {
        project: PathBuf,
    },
    /// A framework for iOS, built for the simulator, where the build has no Xcode project to run.
    IosFramework,
    /// A Gradle task that runs the app on the JVM, such as `run`.
    Jvm {
        task: String,
    },
}

impl Runnable {
    pub fn label(&self) -> String {
        let what = match &self.kind {
            RunKind::AndroidApp { .. } => "Android app".to_owned(),
            RunKind::IosApp { project } => format!("iOS app ({})", project.display()),
            RunKind::IosFramework => "iOS framework".to_owned(),
            RunKind::Jvm { task } => format!("JVM ({task})"),
        };
        format!("{}  {what}", self.module)
    }
}

/// Everything in [project] that lvm-tui can run.
pub fn runnables(project: &Project) -> Vec<Runnable> {
    let xcode = xcode_project(&project.root);
    let mut runnables = Vec::new();
    for module in &project.modules {
        if module.has_plugin("android.application") {
            let id = value(module, "application.applicationId").or_else(|| value(module, "android.namespace"));
            if let Some(application_id) = id {
                runnables.push(Runnable {
                    module: module.path.clone(),
                    platform: Platform::Android,
                    kind: RunKind::AndroidApp { application_id },
                });
            }
        }
        if module.targets.iter().any(|t| t == "ios") && module.framework.is_some() {
            let kind = match &xcode {
                Some(project) => RunKind::IosApp { project: project.clone() },
                None => RunKind::IosFramework,
            };
            runnables.push(Runnable { module: module.path.clone(), platform: Platform::Ios, kind });
        }
        // Kotlin registers jvmRun for every JVM target, so only a main class makes a library an app: the
        // application plugin and Compose Desktop run it with `run`, a JVM target's mainRun with `jvmRun`.
        let source = module.script.as_ref().map(|s| s.source.as_str()).unwrap_or_default();
        let has_main = source.contains("mainClass");
        let task = if module.tasks.iter().any(|t| t == "run")
            || (has_main && (source.contains("compose.desktop") || !module.has_plugin("multiplatform")))
        {
            Some("run")
        } else {
            has_main.then_some("jvmRun")
        };
        if let Some(task) = task {
            runnables.push(Runnable {
                module: module.path.clone(),
                platform: Platform::Jvm,
                kind: RunKind::Jvm { task: task.to_owned() },
            });
        }
    }
    runnables
}

fn value(module: &Module, key: &str) -> Option<String> {
    module.values.iter().find(|row| row.key == key)?.value.as_ref()?.as_str().map(str::to_owned)
}

/// The first `*.xcodeproj` within two directories of [root], such as `iosApp/iosApp.xcodeproj`.
pub fn xcode_project(root: &Path) -> Option<PathBuf> {
    let mut candidates = Vec::new();
    for dir in std::iter::once(root.to_owned()).chain(subdirectories(root)) {
        candidates.extend(subdirectories(&dir).filter(|d| d.extension().is_some_and(|e| e == "xcodeproj")));
    }
    candidates.sort();
    candidates.into_iter().next().map(|p| p.strip_prefix(root).unwrap_or(&p).to_owned())
}

fn subdirectories(dir: &Path) -> impl Iterator<Item = PathBuf> {
    std::fs::read_dir(dir)
        .into_iter()
        .flatten()
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.is_dir() && !p.file_name().is_some_and(|n| n.to_string_lossy().starts_with('.') || n == "build"))
}

/// Quotes [text] for a POSIX shell.
fn quote(text: &str) -> String {
    format!("'{}'", text.replace('\'', "'\\''"))
}

fn task(module: &str, name: &str) -> String {
    if module == ":" { format!(":{name}") } else { format!("{module}:{name}") }
}

/// The tools a run needs: the build's Gradle and the Android SDK's adb.
pub struct RunTools<'a> {
    pub root: &'a Path,
    pub gradle: &'a Path,
    pub adb: Option<&'a Path>,
}

/// The program that runs [runnable] on [device], or why it can't.
pub fn program(runnable: &Runnable, device: Option<&Device>, tools: &RunTools) -> Result<Program, String> {
    let gradle = quote(&tools.gradle.display().to_string());
    match &runnable.kind {
        RunKind::Jvm { task: name } => Ok(Program { args: vec![task(&runnable.module, name)], ..Program::default() }),
        RunKind::IosFramework => {
            let link = task(&runnable.module, "linkDebugFrameworkIosSimulatorArm64");
            Ok(Program { args: vec![link], ..Program::default() })
        }
        RunKind::AndroidApp { application_id } => {
            let device = device.ok_or("Pick an Android device first")?;
            let adb = tools.adb.ok_or("No adb: set ANDROID_HOME to the Android SDK")?;
            let serial = quote(&device.id);
            // resolve-activity finds the launcher activity, as tapping the app's icon would.
            let launch = format!(
                "am start -n $(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER {application_id} | tail -n 1)"
            );
            let script = [
                "set -e".to_owned(),
                format!("ANDROID_SERIAL={serial} {gradle} {} --console=plain", task(&runnable.module, "installDebug")),
                format!("{} -s {serial} shell {}", quote(&adb.display().to_string()), quote(&launch)),
            ]
            .join("\n");
            Ok(shell(script, format!("Run {} on {}", runnable.module, device.name)))
        }
        RunKind::IosApp { project } => {
            let device = device.ok_or("Pick an iOS simulator first")?;
            let udid = quote(&device.id);
            let scheme = project.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
            let derived = tools.root.join("build/lvm-tui/xcode");
            let products = quote(&derived.join("Build/Products/Debug-iphonesimulator").display().to_string());
            let script = [
                "set -e".to_owned(),
                format!("xcrun simctl boot {udid} 2>/dev/null || true"),
                "open -a Simulator".to_owned(),
                format!(
                    "xcodebuild -project {} -scheme {} -configuration Debug -destination id={} -derivedDataPath {} build",
                    quote(&tools.root.join(project).display().to_string()),
                    quote(&scheme),
                    device.id,
                    quote(&derived.display().to_string()),
                ),
                format!("APP=$(find {products} -maxdepth 1 -name '*.app' | head -n 1)"),
                format!("xcrun simctl install {udid} \"$APP\""),
                "BUNDLE=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' \"$APP/Info.plist\")".to_owned(),
                format!("xcrun simctl launch {udid} \"$BUNDLE\""),
            ]
            .join("\n");
            Ok(shell(script, format!("Run {} on {}", runnable.module, device.name)))
        }
    }
}

fn shell(script: String, label: String) -> Program {
    Program {
        program: Some(PathBuf::from("sh")),
        args: vec!["-c".to_owned(), script],
        env: Vec::new(),
        label: Some(label),
    }
}

/// The program that starts [device]: an AVD in its own process, or a simulator.
pub fn start_program(device: &Device, emulator: Option<&Path>) -> Result<Program, String> {
    match device.platform {
        Platform::Android if device.avd => {
            let emulator = emulator.ok_or("No emulator in the Android SDK")?;
            // nohup keeps the emulator running after lvm-tui quits.
            let script = format!(
                "nohup {} -avd {} >/dev/null 2>&1 &",
                quote(&emulator.display().to_string()),
                quote(&device.id)
            );
            Ok(shell(script, format!("Start {}", device.name)))
        }
        Platform::Ios => {
            let script = format!("xcrun simctl boot {} 2>/dev/null || true\nopen -a Simulator", quote(&device.id));
            Ok(shell(script, format!("Boot {}", device.name)))
        }
        _ => Err(format!("{} is running already", device.name)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::devices::State;
    use std::fs;

    fn write(root: &Path, file: &str, text: &str) {
        let path = root.join(file);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, text).unwrap();
    }

    fn build() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        write(root, "settings.gradle.kts", "include(\":app\", \":shared\", \":desktop\")\n");
        write(
            root,
            "app/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.android.application\") }\nlvm { android { namespace = \"com.example.app\" } }\n",
        );
        write(
            root,
            "shared/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { ios { framework { baseName = \"Shared\" } } } }\n",
        );
        write(
            root,
            "desktop/build.gradle.kts",
            "plugins { id(\"tech.annexflow.lvm.multiplatform\") }\nlvm { targets { jvm() } }\ncompose.desktop { application { mainClass = \"MainKt\" } }\n",
        );
        dir
    }

    fn device(platform: Platform, id: &str) -> Device {
        Device { platform, id: id.into(), name: format!("Device {id}"), state: State::Ready, avd: false }
    }

    #[test]
    fn finds_android_ios_and_jvm_apps() {
        let dir = build();
        let project = Project::load(dir.path());
        let labels: Vec<String> = runnables(&project).iter().map(Runnable::label).collect();
        assert_eq!(labels, [":app  Android app", ":desktop  JVM (run)", ":shared  iOS framework"]);

        fs::create_dir_all(dir.path().join("iosApp/iosApp.xcodeproj")).unwrap();
        let project = Project::load(dir.path());
        let ios = runnables(&project).into_iter().find(|r| r.platform == Platform::Ios).unwrap();
        assert_eq!(ios.kind, RunKind::IosApp { project: PathBuf::from("iosApp/iosApp.xcodeproj") });
    }

    #[test]
    fn installs_with_gradle_on_the_chosen_device_and_launches() {
        let dir = build();
        let project = Project::load(dir.path());
        let app = runnables(&project).remove(0);
        let tools = RunTools { root: dir.path(), gradle: Path::new("/b/gradlew"), adb: Some(Path::new("/sdk/adb")) };

        let run = program(&app, Some(&device(Platform::Android, "emulator-5554")), &tools).unwrap();

        assert_eq!(run.program, Some(PathBuf::from("sh")));
        let script = &run.args[1];
        assert!(
            script.contains("ANDROID_SERIAL='emulator-5554' '/b/gradlew' :app:installDebug --console=plain"),
            "{script}"
        );
        assert!(script.contains("'/sdk/adb' -s 'emulator-5554' shell 'am start -n $(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.app | tail -n 1)'"), "{script}");
        assert!(program(&app, None, &tools).is_err(), "it needs a device");
    }

    #[test]
    fn quotes_what_it_passes_to_the_shell() {
        assert_eq!(quote("it's"), "'it'\\''s'");
    }
}
