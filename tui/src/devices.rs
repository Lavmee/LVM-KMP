//! Devices to run an app on: Android devices and emulators, AVDs that aren't running, and iOS simulators.

use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde_json::Value;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Platform {
    Android,
    Ios,
    Jvm,
}

impl Platform {
    pub fn label(self) -> &'static str {
        match self {
            Self::Android => "Android",
            Self::Ios => "iOS",
            Self::Jvm => "JVM",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum State {
    /// Ready to install on.
    Ready,
    /// A device adb sees but can't use, such as `offline` or `unauthorized`.
    Unusable(String),
    /// An AVD or a simulator that isn't running; it can be started.
    Stopped,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Device {
    pub platform: Platform,
    /// The adb serial, the AVD name or the simulator's UDID.
    pub id: String,
    pub name: String,
    pub state: State,
    /// An AVD that `emulator -avd` starts, rather than a device adb sees.
    pub avd: bool,
}

/// The tools that run apps, found in the Android SDK and Xcode.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Tools {
    pub adb: Option<PathBuf>,
    pub emulator: Option<PathBuf>,
    pub simctl: bool,
}

impl Tools {
    pub fn find(sdk: Option<&Path>) -> Self {
        let tool = |relative: &str| sdk.map(|sdk| sdk.join(relative)).filter(|path| path.is_file());
        let suffix = if cfg!(windows) { ".exe" } else { "" };
        Self {
            adb: tool(&format!("platform-tools/adb{suffix}")),
            emulator: tool(&format!("emulator/emulator{suffix}")),
            simctl: cfg!(target_os = "macos") && succeeds(Command::new("xcrun").args(["simctl", "help"])),
        }
    }
}

/// The Android SDK: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` of the build's `local.properties`, or where
/// Android Studio installs it.
pub fn android_sdk(root: &Path) -> Option<PathBuf> {
    let home = directories::BaseDirs::new().map(|dirs| dirs.home_dir().to_owned());
    ["ANDROID_HOME", "ANDROID_SDK_ROOT"]
        .iter()
        .filter_map(std::env::var_os)
        .map(PathBuf::from)
        .chain(
            crate::project::properties::Properties::read(&root.join("local.properties"))
                .and_then(|p| p.get("sdk.dir").map(PathBuf::from)),
        )
        .chain(home.iter().flat_map(|home| [home.join("Library/Android/sdk"), home.join("Android/Sdk")]))
        .find(|dir| dir.is_dir())
}

fn succeeds(command: &mut Command) -> bool {
    command.stdout(Stdio::null()).stderr(Stdio::null()).status().is_ok_and(|status| status.success())
}

fn output(command: &mut Command) -> Option<String> {
    let output = command.stderr(Stdio::null()).output().ok()?;
    output.status.success().then(|| String::from_utf8_lossy(&output.stdout).into_owned())
}

/// Every device the tools find, running ones first. It runs adb, the emulator and simctl, so it takes a moment.
pub fn probe(tools: &Tools) -> Vec<Device> {
    let mut devices = Vec::new();
    if let Some(adb) = &tools.adb {
        let mut running = parse_adb_devices(&output(Command::new(adb).args(["devices", "-l"])).unwrap_or_default());
        // An emulator names its AVD, which then isn't listed again as one to start.
        let mut running_avds = Vec::new();
        for device in running.iter_mut().filter(|d| d.id.starts_with("emulator-")) {
            if let Some(avd) = output(Command::new(adb).args(["-s", &device.id, "emu", "avd", "name"]))
                .and_then(|text| text.lines().next().map(|l| l.trim().to_owned()))
                .filter(|name| !name.is_empty())
            {
                device.name = format!("{} ({})", avd.replace('_', " "), device.id);
                running_avds.push(avd);
            }
        }
        devices.extend(running);
        if let Some(emulator) = &tools.emulator {
            let avds = parse_avds(&output(Command::new(emulator).arg("-list-avds")).unwrap_or_default());
            devices.extend(avds.into_iter().filter(|avd| !running_avds.contains(&avd.id)));
        }
    }
    if tools.simctl {
        devices.extend(parse_simulators(
            &output(Command::new("xcrun").args(["simctl", "list", "devices", "--json"])).unwrap_or_default(),
        ));
    }
    devices
}

/// `adb devices -l`: `emulator-5554  device product:sdk model:Pixel_8 device:emu transport_id:1`.
pub fn parse_adb_devices(text: &str) -> Vec<Device> {
    text.lines()
        .skip_while(|line| !line.starts_with("List of devices"))
        .skip(1)
        .filter_map(|line| {
            let mut words = line.split_whitespace();
            let id = words.next()?.to_owned();
            let state = words.next()?.to_owned();
            let model = line.split_whitespace().find_map(|w| w.strip_prefix("model:")).map(|m| m.replace('_', " "));
            Some(Device {
                platform: Platform::Android,
                name: model.map_or_else(|| id.clone(), |model| format!("{model} ({id})")),
                state: if state == "device" { State::Ready } else { State::Unusable(state) },
                id,
                avd: false,
            })
        })
        .collect()
}

/// `emulator -list-avds`: one name per line, after any log lines of the emulator itself.
pub fn parse_avds(text: &str) -> Vec<Device> {
    text.lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.contains(' ') && !line.contains('|'))
        .map(|name| Device {
            platform: Platform::Android,
            id: name.to_owned(),
            name: format!("{} (AVD)", name.replace('_', " ")),
            state: State::Stopped,
            avd: true,
        })
        .collect()
}

/// `xcrun simctl list devices --json`: the available iOS simulators, booted ones and newer runtimes first.
pub fn parse_simulators(json: &str) -> Vec<Device> {
    let Ok(value) = serde_json::from_str::<Value>(json) else { return Vec::new() };
    let Some(runtimes) = value["devices"].as_object() else { return Vec::new() };
    let mut simulators: Vec<(bool, String, Device)> = runtimes
        .iter()
        .filter(|(runtime, _)| runtime.contains(".iOS-"))
        .flat_map(|(runtime, devices)| {
            let version = runtime.rsplit(".iOS-").next().unwrap_or_default().replace('-', ".");
            devices.as_array().into_iter().flatten().filter(|d| d["isAvailable"] != Value::Bool(false)).map(move |d| {
                let booted = d["state"] == "Booted";
                let device = Device {
                    platform: Platform::Ios,
                    id: d["udid"].as_str().unwrap_or_default().to_owned(),
                    name: format!("{} (iOS {version})", d["name"].as_str().unwrap_or_default()),
                    state: if booted { State::Ready } else { State::Stopped },
                    avd: false,
                };
                (booted, version.clone(), device)
            })
        })
        .collect();
    simulators.sort_by(|a, b| {
        b.0.cmp(&a.0).then_with(|| crate::version::compare(&b.1, &a.1)).then_with(|| a.2.name.cmp(&b.2.name))
    });
    simulators.into_iter().map(|(_, _, device)| device).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_adb_devices_with_their_models() {
        let text = "* daemon started successfully\nList of devices attached\nemulator-5554          device product:sdk_gphone64 model:sdk_gphone64_arm64 device:emu64a transport_id:1\nR58M123 unauthorized usb:1-1 transport_id:2\n\n";
        let devices = parse_adb_devices(text);
        assert_eq!(devices.len(), 2);
        assert_eq!(devices[0].name, "sdk gphone64 arm64 (emulator-5554)");
        assert_eq!(devices[0].state, State::Ready);
        assert_eq!(devices[1].state, State::Unusable("unauthorized".into()));
    }

    #[test]
    fn reads_avds_without_the_emulator_s_own_lines() {
        let text = "INFO    | Storing crashdata in: /tmp/x\nMedium_Phone\nPixel_8_API_35\n";
        let names: Vec<_> = parse_avds(text).into_iter().map(|d| d.id).collect();
        assert_eq!(names, ["Medium_Phone", "Pixel_8_API_35"]);
    }

    #[test]
    fn lists_available_ios_simulators_booted_and_newest_first() {
        let json = r#"{"devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-26-5": [{"udid": "A", "name": "iPhone 17", "state": "Shutdown", "isAvailable": true}],
            "com.apple.CoreSimulator.SimRuntime.iOS-27-2": [
                {"udid": "B", "name": "iPhone 18", "state": "Shutdown", "isAvailable": true},
                {"udid": "C", "name": "Old", "state": "Shutdown", "isAvailable": false}],
            "com.apple.CoreSimulator.SimRuntime.watchOS-12-0": [{"udid": "W", "name": "Watch", "state": "Booted", "isAvailable": true}],
            "com.apple.CoreSimulator.SimRuntime.iOS-27-0": [{"udid": "D", "name": "iPhone 18 Pro", "state": "Booted", "isAvailable": true}]
        }}"#;
        let simulators = parse_simulators(json);
        let ids: Vec<_> = simulators.iter().map(|d| d.id.as_str()).collect();
        assert_eq!(ids, ["D", "B", "A"]);
        assert_eq!(simulators[0].name, "iPhone 18 Pro (iOS 27.0)");
        assert_eq!(simulators[0].state, State::Ready);
    }
}
