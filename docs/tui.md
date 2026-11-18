# The lvm command

`lvm` is a terminal app for Gradle builds that use LVM's plugins. It shows the build's modules and the values of `lvm { }` with where each one comes from, checks the build for what would fail LVM's plugins and fixes it, runs Gradle tasks with their test results and detekt issues, runs apps on devices and follows their logs, moves the build to a new LVM release, and creates builds, modules and dependencies. Each LVM release comes with an `lvm` command of the same version. It is built with [Ratatui](https://ratatui.rs) and works in any terminal on macOS, Linux and Windows.

## Install

Every [GitHub Release](https://github.com/Lavmee/LVM-KMP/releases) of LVM has an archive of the `lvm` command for macOS (`aarch64-apple-darwin`, `x86_64-apple-darwin`), Linux (`x86_64-unknown-linux-gnu`, `aarch64-unknown-linux-gnu`) and Windows (`x86_64-pc-windows-msvc`), each with its SHA-256 checksum. Download the archive for your machine, check it, and put `lvm` on your `PATH`:

```bash
gh release download vX.Y.Z --repo Lavmee/LVM-KMP --pattern 'lvm-tui-vX.Y.Z-aarch64-apple-darwin.tar.gz*'
shasum -a 256 -c lvm-tui-vX.Y.Z-aarch64-apple-darwin.tar.gz.sha256
tar -xzf lvm-tui-vX.Y.Z-aarch64-apple-darwin.tar.gz
```

The binaries are not signed. macOS refuses to open a binary that a browser downloaded until you allow it in System Settings > Privacy & Security; files downloaded with `gh` or `curl` open without that step.

With Rust 1.96 or newer, Cargo builds `lvm` from the tag of a release. Its crate is `lvm-tui`:

```bash
cargo install --git https://github.com/Lavmee/LVM-KMP --tag vX.Y.Z lvm-tui
```

From a checkout of this repository, `cargo install --path tui` builds the version of the checkout.

On Linux, `lvm` is also the command of LVM2, which manages disk volumes. Your shell runs the `lvm` that comes first in your `PATH`: put the directory with LVM's `lvm`, such as `~/.cargo/bin`, before `/usr/sbin` and `/usr/bin`. `sudo lvm` still runs LVM2's.

## Start

Run `lvm` in any directory of a build, or pass the directory: it walks up to the build's settings script.

```bash
lvm path/to/build
```

`lvm` reads the build's files without running Gradle, so it starts at once. For exact values, press `r`: it runs the `lvmReport` task, which LVM 0.4.0 and newer have, and reads what the plugins actually set up. The [Report](../README.md#report) section of the README describes the task. With an older LVM, `lvm` shows what the files say, and Modules marks each value as coming from the build files or from `lvmReport`.

Outside a build, `lvm` offers to create one. It remembers the tab, the module and the device of each build in your data directory, never in the build.

## Tabs

The number keys and `tab` switch tabs. Library and Compose are there only when a module applies `tech.annexflow.lvm.publish` or `tech.annexflow.lvm.compose`, and take the numbers after Updates.

| Key | Tab | What it shows |
|---|---|---|
| `1` | Overview | The LVM version, Gradle, Kotlin and AGP with their minimum and tested versions, the options of the settings plugin and a summary of the modules |
| `2` | Modules | Each module's plugins, targets, layout and dependencies, and every value of `lvm { }` with its source |
| `3` | Tasks | Gradle tasks of the build or a module, their log, test results, detekt issues and the builds of this session |
| `4` | Run | Apps of the build, the devices to run them on and the app's log |
| `5` | Doctor | What would fail LVM's plugins or the build, with fixes |
| `6` | Updates | LVM's releases on Maven Central and what moving to one changes |
| | Library | The published modules: their POM, the signing setup, the ABI dump and a dry run to Maven local |
| | Compose | The Compose compiler's metrics and reports |

These keys work everywhere:

| Key | Action |
|---|---|
| `?` | The keys of every tab |
| `ctrl-p`, `:` | The command palette, which finds every action by name |
| `r` | Run `lvmReport`, then read the reports |
| `R` | Read the build's files again |
| `ctrl-c` | Cancel the running build, or quit when none runs |
| `q` | Quit |

### Changes to the build

Every action that changes files shows the change as a diff first, and `y` applies it. Files move with `git mv` where git tracks them, so their history stays connected. After a change to the build, `lvm` offers to run `lvmReport` and `build`.

In Modules, `e` edits the selected value and asks where to set it: `gradle.properties`, the `lvm { }` block of the settings script, or the module's own `lvm { }`. It checks the value against its type and says whether a list is appended to the build-wide list or replaces it. `n` creates a module with the layout and conventions of the build, `d` adds an LVM library with what it needs, such as the serialization plugin for `lvm-storage`, and `m` moves a module's sources to LVM's flat layout.

### Tasks

`enter` runs the selected task and `c` types a Gradle command. `v` switches between the log, the test results, the detekt issues and the builds of this session. In the log, `n` and `N` go to the next and previous error, and `o` opens its file in `$VISUAL` or `$EDITOR`. In the test results, `enter` opens the failing line, `t` runs the selected test again and `a` runs every failed test again.

### Run

Run lists the Android apps, the iOS apps of an Xcode project in the build, the iOS frameworks when there is no Xcode project, and the JVM apps with a main class. Pick an app and a device, and `enter` runs it: an Android app installs with `installDebug` on the device and starts from its launcher activity, an iOS app builds with `xcodebuild` for the simulator and launches there, and a JVM app runs with Gradle. `s` starts the selected emulator or simulator, and `f` looks for devices again. Running apps needs a Unix shell, so on Windows only JVM apps and iOS frameworks run.

`a` follows the app's log: logcat of the app's process on Android, or the unified log of the app on a simulator. A JVM app's output is its log. `F` follows a file that `lvm-logger-file` writes: an absolute path on this computer, or a path in the app's files on an Android device, which needs a debuggable build. The log follows the file when the logger rolls it. `L` sets the lowest level shown, `/` searches, `space` pauses, `C` clears and `x` stops the log.

### Doctor

Doctor checks the build for what would fail LVM's plugins, such as plugins missing from the root project, unknown keys and values that don't parse, missing namespaces, sources outside the flat layout, and Gradle, Kotlin or AGP versions that LVM doesn't support. It also checks the machine and the repository: the Android SDK, Xcode, the JDK, a `local.properties` that git tracks, and publishing secrets in the build's `gradle.properties`. `f` fixes the selected finding where a fix exists, `F` fixes them all, and `o` opens the file.

### Updates

Updates lists LVM's releases and compares the selected one with the build's: the tested versions of Gradle, Kotlin and AGP, and the minimums. `u` moves the build to it: the version of the settings plugin, `catalogVersion`, `lvm-bom`, coordinates of LVM's artifacts, LVM's plugins in build logic and LVM's entries in the build's own version catalog.

### Library

Library shows the artifact ID and the POM values of each published module with their source, and where the Maven Central credentials and the signing key are set, without their values. `c` runs `checkKotlinAbi` and shows how the ABI changed, with removals as possible breaking changes, `u` updates the dump in `api/`, and `p` publishes to Maven local without signing and lists the files. `lvm` never publishes to Maven Central.

### Compose

`c` compiles the selected module once with the Compose compiler's reports and metrics on, through Gradle properties, so the build's files stay as they are. It compiles one target, since every compilation of a module writes its reports under the same names. The tab shows the metrics of each compilation, the classes that aren't stable with the properties that make them so, the composables that don't skip and those whose arguments aren't stable, which recompose whenever a new instance arrives. `enter` opens the declaration.

## Commands

The commands below run without the interface, for scripts and CI. Each takes the build's directory, by default the current one.

`lvm check` runs the doctor's checks and exits with 1 while errors remain. `--json` prints the findings as JSON, and `--fix` applies every fix, then checks again:

```bash
lvm check --json
```

`lvm upgrade` moves the build to the latest LVM release, or to the one `--to` names. `--dry-run` prints the change without writing it:

```bash
lvm upgrade --to 0.4.0 --dry-run
```

`lvm new` creates a build with LVM: a shared Kotlin Multiplatform module and an Android app that uses it, with the Gradle wrapper of the LVM release. Without `--yes`, it asks for the details in a form. `lvm new --help` lists the options, such as `--targets`, `--compose` and `--no-app`.

```bash
lvm new my-app --group com.example.myapp --compose --yes
```

`lvm report` prints the build as `lvm` reads it, as JSON.
