---
name: check
description: Always use this skill to verify a change locally before committing or opening a pull request in the LVM-KMP repository
---

# Verifying a Change

Keep this file in sync with [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) and [`.github/workflows/tui.yml`](../../../.github/workflows/tui.yml).

Run these by default:

```bash
./gradlew -p blueprint check # blueprint unit tests and TestKit fixture builds
./gradlew build # every module on every target, including iOS simulator tests
```

Blueprint is an included build, so the root `build` does not run its tests. Run both. Both builds compare each module's public API with its dump in `api/`; after an intended API change, run `updateKotlinAbi` in the same build and commit the dumps.

## tui

Run these in `tui/` when a change touches `tui/` or a file it embeds, such as `gradle.properties`, `settings.gradle.kts`, the Gradle wrapper or `blueprint/common/lvm-keys.json`:

```bash
cargo fmt --check
cargo clippy --all-targets --locked -- -D warnings
cargo test --locked
cargo deny check # licenses and advisories of the dependencies; needs cargo-deny
./embed-files.sh && cargo package --locked --allow-dirty # builds the crate from its package for crates.io
```

The UI tests compare screens with the snapshots in `tui/tests/snapshots`. After an intended change to a screen, review each new `.snap.new` file and accept it with `cargo insta accept`, or run `INSTA_UPDATE=always cargo test`. The `version` in `tui/Cargo.toml` must equal `VERSION_NAME`.

## Requirements

- macOS with Xcode for `./gradlew build`: iOS klibs, frameworks and simulator tests build only there. Blueprint's tests run on any host; CI runs them on Linux for pull requests from forks.
- An Android SDK, found through `ANDROID_HOME` or `sdk.dir` in `local.properties`.
- JDK 21 for the Gradle daemon, as set in `gradle/gradle-daemon-jvm.properties`.

## Only on user request

```bash
# rerun every task, ignoring up-to-date checks and the build cache
./gradlew -p blueprint check --rerun-tasks
./gradlew build --rerun-tasks

# configure the build with Isolated Projects; every reported problem that comes from blueprint is a bug
./gradlew help -Dorg.gradle.unsafe.isolated-projects=true

# list deprecations; fix the ones that come from blueprint, report the ones that come from other plugins
./gradlew build --warning-mode all --no-configuration-cache
```

## New modules

- A new blueprint plugin needs its module in `blueprint/settings.gradle.kts` with `id("lvm.blueprint-publish")`, a `gradlePlugin { }` registration, an `apply false` entry in the root `build.gradle.kts`, a `blueprint-<name>` entry in `versions/build.gradle.kts` and a row in the README plugin table.
- A new blueprint module commits its ABI dump: run `./gradlew -p blueprint :<name>:updateKotlinAbi` and commit `api/`.
- A new plugin in an existing blueprint module needs its `gradlePlugin { }` registration, an `apply false` entry in the root `build.gradle.kts`, a `blueprint-<name>` entry in `versions/build.gradle.kts` and a row in the README plugin table.
- A new library goes in `runtime/<name>` (Gradle path `:runtime:<name>`, flat, with hyphens: `runtime/logger-console`) and needs an `include` in the root `settings.gradle.kts`, `id("tech.annexflow.lvm.publish")`, an entry in `bom/build.gradle.kts` and in `versions/build.gradle.kts`, and its ABI dump: run `./gradlew :runtime:<name>:updateKotlinAbi` and commit `api/`. A new sample module needs only the `include`.
- A new compiler plugin goes in `compiler/<name>` (Gradle path `:compiler:<name>`) and needs what a new library needs.
- A module of this build that applies `tech.annexflow.lvm.main-context` needs the dependency substitution of `samples/main-context-sample/build.gradle.kts`.
