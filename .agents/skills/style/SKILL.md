---
name: style
description: Always use this skill before writing or editing Kotlin code, Gradle build logic or KDoc in the LVM-KMP repository
---

# Code Style

## General

* Follow the official Kotlin code style. Modules apply `tech.annexflow.lvm.quality`, so `./gradlew build` runs detekt on them.
* Keep related code together: a class is followed by the extensions and helpers that belong to it, before the next class in the file.
* Make declarations `internal` unless consumers need them. Public API is hard to take back once released, so add it deliberately.
* Use only ASCII characters in code, comments and strings: `->` instead of an arrow, `...` instead of an ellipsis character, no em dash.

## Blueprint plugins

* Configure lazily. Apply values with `Property.convention()` so a value set in a build script wins, pass `Provider`s instead of reading values early, and configure AGP in `androidComponents.finalizeDsl`. Never use `afterEvaluate`: the order of blocks in a build script must not matter.
* Stay compatible with the configuration cache and Isolated Projects. A project never reads the state of another project, and task actions never use `Project`.
* Every value a plugin sets has a default and can be changed. A build-wide value resolves through `LvmValues`: the `lvm.<section>.<name>` Gradle property, then the value forwarded from the settings `lvm { }` block, then the default in `LvmDefaults`. A module's own `lvm { }` block is stronger than all of them. A new value needs its key in `LvmKeys`, its default in `LvmDefaults` and a forwarding line in `LvmSettingsExtension.forwardedValues()`.
* Lists set in a module are appended to the build-wide list. Scalars replace the build-wide value.
* Every automatic behavior can be switched off, including dependencies a plugin adds.
* Values set with the native `kotlin { }` and `android { }` DSL win over `lvm { }`.
* One plugin configures one concern and adds its own section to the `lvm { }` extension.
* AGP, the Kotlin Gradle plugin and detekt are `compileOnly` dependencies, so the consuming build chooses their versions. `lvm-blueprint-common` references none of them; only the plugin that needs one does.
* Plugins never read the consumer's version catalog. Versions and coordinates that blueprint bakes in come from `gradle/libs.versions.toml` through the `buildConfig { }` block in `blueprint/common/build.gradle.kts`, never from literals in code.
* `gradle/libs.versions.toml` is published as the `lvm-versions` catalog. A tool that only builds LVM, such as a plugin used by `blueprint/`, is declared where that build needs it, for example in `pluginManagement` of `blueprint/settings.gradle.kts`.
* An error tells the user what to set and where, for example `set group = "..." in the module, or lvm { android { namespace = "..." } }`.

## Libraries

* Public declarations state their visibility and return type, as strict explicit API mode requires.
* Code that uses Android APIs goes to `androidMain`; everything else goes to `commonMain`. Every library builds for Android, iOS and JVM.
* No dependency injection annotations. LVM works with any DI framework or none.

## Documentation

* Write in plain English with simple sentences.
* KDoc describes what a declaration is or does and how to use it. Mention implementation details only when a caller needs them.
* Describe the current state only; never refer to earlier versions ("this used to be A").
* Do not list every use or implementation of a declaration; such lists go stale.
* Do not describe who uses a declaration ("used by X to do Y"). Describe the declaration itself.
* A comment explains why the code is written this way, not what the next line does.

## Tests

* Blueprint plugins are tested through TestKit fixture builds (`GradleFixture` in `blueprint/common/src/testFixtures`), which apply the plugins from source the way a consumer would. Assert on the build's outcome, such as compiled class files, task output or the fact that a build fails.
* Fixture builds are slow. Test pure logic, such as value parsing or version comparison, with plain unit tests.
* Every test protects a specific behavior or catches a plausible bug. Before writing one, name the wrong behavior that would make it fail.
* Do not write tests that only restate hardcoded values, and do not depend on the exact wording of messages.
* Derive expected results from the intended behavior, not by repeating the implementation.
* Keep tests sensitive to broken behavior and tolerant of refactorings that keep the behavior.
* Name a test with a backticked sentence that states the behavior.
* Delete tests that protect nothing.
