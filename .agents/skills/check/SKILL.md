---
name: check
description: Always use this skill to verify a change locally before committing or opening a pull request in the LVM-KMP repository
---

# Verifying a Change

Keep this file in sync with [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml).

Run these by default:

```bash
./gradlew -p blueprint check # blueprint unit tests and TestKit fixture builds
./gradlew build # every module on every target, including iOS simulator tests
```

Blueprint is an included build, so the root `build` does not run its tests. Run both.

## Requirements

- macOS with Xcode. iOS klibs, frameworks and simulator tests build only there.
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

- A new blueprint plugin needs its module in `blueprint/settings.gradle.kts`, a `gradlePlugin { }` registration, an `apply false` entry in the root `build.gradle.kts` and a row in the README plugin table.
- A new library or sample module needs an `include` in the root `settings.gradle.kts`.
