---
name: check
description: Checks a Gradle build that uses LVM's plugins with lvm check, which finds without running Gradle what would fail LVM's plugins or the build - plugins missing from the root project, unknown or malformed lvm.* keys, missing namespaces, sources outside the flat layout, unsupported Gradle, Kotlin or AGP versions, a missing Android SDK, Xcode or JDK, a local.properties that git tracks and secrets in gradle.properties - and fixes what it can. Use after changing an LVM build's scripts, properties or layout, or when its Gradle build fails in LVM's plugins.
argument-hint: "[build directory]"
allowed-tools:
  - Bash(lvm --version)
  - Bash(lvm check --json *)
  - Bash(lvm report *)
---

# Check a build with LVM

Arguments: `$ARGUMENTS`. They name a directory of the build; with none, check the build of the current directory. lvm walks up from the directory to the build's `settings.gradle.kts`.

## 1. Run the check

Run `lvm --version` first: it must print `lvm` and a version, such as `lvm 0.4.0`. If the command is missing, or is LVM2's `lvm` on Linux, follow "Read the build with lvm" in the `conventions` skill and stop.

Run `lvm check --json`, followed by the directory when one was given. It exits with 1 while errors remain, so exit code 1 with JSON on stdout is a result, not a failure of the command. An error such as `no Gradle build at or above` means the directory isn't in a Gradle build.

The JSON has `findings`, each with:

* `rule`: the check's ID, such as `root-plugins` or `android-sdk`.
* `severity`: `error`, `warning` or `info`.
* `message`: what is wrong.
* `file` and `line`: where, relative to the build's root, when the finding has a place.
* `module`: the module's Gradle path, when the finding belongs to one.
* `fix`: `null`, or the change that resolves it, by `kind`.

## 2. Report

Tell the user the errors first, then the warnings, each with its place (`file:line`, the module, or the machine when the finding has neither), the message in your words and whether lvm can fix it. Mention infos only when they matter for what the user is doing. Don't paste the JSON. When nothing is found, say so in one line.

## 3. Fix

These are the fixes that lvm applies:

| `kind` | What it changes |
|---|---|
| `declareRootPlugins` | Adds the plugin declarations in `lines`, with `apply false`, to the root `build.gradle.kts` |
| `renameProperty` | Renames the key `from` on `line` of `gradle.properties` to `lvm.<to>` |
| `setGroup` | Adds `group = "<group>"` to the build script of `module` |
| `migrateFlatLayout` | Moves the sources of `module` to the flat layout, with `git mv` where git tracks them |
| `untrackLocalProperties` | Removes `local.properties` from git, keeping the file, and adds it to `.gitignore` |

`lvm check --fix` applies every fix of the build at once, prints the diff and checks again. Run it only after the user agrees to all of the fixes; it writes files. When the user wants only some of them, or when `migrateFlatLayout` would move many files, make those changes yourself instead, then run `lvm check --json` again.

A finding without a fix needs a change by hand. Propose it, and make it once the user agrees. For the machine, such as `android-sdk`, `xcode-select` or the JDK, tell the user what to install or set. For the Android SDK, that is `ANDROID_HOME`, or `sdk.dir` in `local.properties`, which git must not track: when the SDK is in its usual place, such as `~/Library/Android/sdk` on macOS or `~/Android/Sdk` on Linux, offer to write `sdk.dir` there, and check that `.gitignore` lists `local.properties`. Never move secrets that the check found into another file that git tracks; they belong in `~/.gradle/gradle.properties` or the CI's secrets.

## 4. Confirm with Gradle

lvm reads the build's files and doesn't run Gradle, so it can't see everything. Once no errors remain, or only errors about the machine that the user will fix, offer to run `./gradlew lvmReport` and the narrowest task that covers the change, such as `./gradlew :shared:build`. Gradle fails on the same machine errors, such as a missing Android SDK, until they are fixed. iOS targets build only on macOS with Xcode.
