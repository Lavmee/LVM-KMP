---
name: conventions
description: How a Gradle build that uses LVM's convention plugins works - the values of lvm { } and where each one comes from, the plugins and the root project's declarations, the lvmLibs catalog, the flat source layout and the lvm command that reads the build. Use when working in a build whose settings.gradle.kts applies tech.annexflow.lvm.settings, or whose build scripts apply tech.annexflow.lvm.* plugins or lvmLibs aliases.
allowed-tools:
  - Bash(lvm --version)
  - Bash(lvm report *)
---

# Builds with LVM

[LVM](https://github.com/Lavmee/LVM-KMP) is a set of Gradle convention plugins, called blueprint, and Kotlin Multiplatform libraries for Android, iOS and JVM. A build uses it when its `settings.gradle.kts` applies `tech.annexflow.lvm.settings`. The plugins set up Kotlin Multiplatform, Android, Compose, detekt and publishing from one `lvm { }` block with defaults, so most of a module's build script is the list of its plugins.

## Read the build with lvm

The `lvm` command reads a build without running Gradle. Run `lvm --version` first: it must print `lvm` and a version, such as `lvm 0.4.0`. On Linux, `lvm` can also be LVM2's command for disk volumes, which prints something else. When the command is missing, or is LVM2's, tell the user how to install it: `cargo install lvm-tui`, or the archive for their machine from [the GitHub Releases](https://github.com/Lavmee/LVM-KMP/releases), as [Install](https://github.com/Lavmee/LVM-KMP/blob/main/docs/tui.md#install) describes. Until then, run `./gradlew lvmReport` and read the reports it writes, as the last part of this section describes.

`lvm report`, run in any directory of the build, prints the build as JSON. Read it before you read the build scripts one by one, and take values from it instead of from memory:

* `lvm`: the LVM version. `kind` is `pinned` with `version`, `fromSource` for an included build, `unpinned` when the settings script hides the version, or `none` without the settings plugin.
* `toolchain`: Gradle, Kotlin and AGP, each with the `actual` version, the `minimum` that LVM requires and the `tested` one. A `null` means lvm couldn't tell from the files; an `actual` of `null` usually means the root build script takes the plugin from `lvmLibs`, so the build uses the version its LVM release was tested with.
* `options`: the options of the settings plugin, such as `catalogName`.
* `modules`: each module's `path`, `dir`, LVM `plugins`, `targets`, `layout` (`flat`, `standard`, `mixed` or `unknown`), `projectDependencies`, `lvmDependencies`, `publication` and `values`.
* `values`: every value of `lvm { }` that the module uses, with its `key`, `value` and `source`: `default` for blueprint's own default, `settings` for the `lvm { }` block of `settings.gradle.kts`, `property` for an `lvm.*` Gradle property, from the build's `gradle.properties`, `~/.gradle/gradle.properties` or `-P`, or `module` for the module's own `lvm { }` in its build script.
* `origin`: `files` when lvm read the build files, `report` when the value comes from Gradle's `lvmReport` task. `report.state` says whether the reports are `fresh`, `stale` or `missing`, or `unsupported` when the build's LVM is older than 0.4.0 and has no `lvmReport`: then every value comes from the files.

Values read from the files are close but not exact. For exact values, run `./gradlew lvmReport`, which LVM 0.4.0 and newer have, then `lvm report` again: it reads what the plugins actually set up. Without the lvm command, read `build/lvm/report/<path>/project.json` of the root project, where `<path>` is the module's path with `/` for `:`.

## Where values come from

Every value has a default. A build changes it in four places, weakest first:

1. Blueprint's default.
2. `lvm { }` in `settings.gradle.kts`: the build-wide value.
3. An `lvm.*` Gradle property, such as `lvm.android.minSdk=26` in `gradle.properties` or `-Plvm.android.minSdk=26`.
4. `lvm { }` in the module's build script.

A stronger scalar value replaces a weaker one. For lists, a module's value is appended to the build-wide list for `kotlin.optIns`, `kotlin.freeCompilerArgs`, `quality.config`, `compose.stabilityConfigurationFiles`, `application.proguardFiles`, `library.consumerProguardFiles` and `library.resourcePrefixDroppedSegments`, and replaces it for `ios.architectures`, `quality.source` and `mainContext.platforms`. A list as a Gradle property is comma-separated. A module can't take an entry out of a build-wide list, except for opt-ins: `lvm { kotlin { excludedOptIns } }`, which only a module sets, leaves opt-ins out of its compilations, build-wide ones included. Use it for a build-wide opt-in whose library the module doesn't depend on, whose unresolved marker otherwise fails the module with `kotlin.warningsAsErrors`.

Set a value in the narrowest place that covers what the user asked for: one module's value in its own `lvm { }`, a value for every module in the `lvm { }` block of `settings.gradle.kts`, and a value that changes per machine or per CI run as a Gradle property. Values set with the native `android { }` and `kotlin { }` DSL win over `lvm { }`, so use them only for what `lvm { }` doesn't cover.

## Plugins

The root `build.gradle.kts` declares every plugin that any module applies, with `apply false`, so that all of them load in one classloader. Blueprint doesn't bundle AGP, Kotlin, Compose or detekt, so their plugins are declared there too. When a module starts to apply a plugin that the root project doesn't declare yet, add the declaration. `lvm check` reports a missing one and can add it.

| Plugin ID | Alias in `lvmLibs` | What it sets up |
|---|---|---|
| `tech.annexflow.lvm.settings` | (settings script, with a version) | The `lvmLibs` catalog, the repositories and the build-wide `lvm { }` |
| `tech.annexflow.lvm.multiplatform` | `lvmLibs.plugins.blueprint.multiplatform` | Kotlin Multiplatform: the targets of `lvm { targets { } }`, source sets and compiler options |
| `tech.annexflow.lvm.jvm` | `lvmLibs.plugins.blueprint.jvm` | A Kotlin/JVM module, such as a KSP processor: the Java version, compiler options and `kotlin-test` |
| `tech.annexflow.lvm.android.application` | `lvmLibs.plugins.blueprint.android.application` | An Android application |
| `tech.annexflow.lvm.android.library` | `lvmLibs.plugins.blueprint.android.library` | An Android library without Kotlin Multiplatform, such as one with product flavors |
| `tech.annexflow.lvm.android.test` | `lvmLibs.plugins.blueprint.android.test` | An Android test module of `com.android.test`, such as a macrobenchmark or a baseline profile generator |
| `tech.annexflow.lvm.compose` | `lvmLibs.plugins.blueprint.compose` | The Compose compiler, and Compose Multiplatform in a Kotlin Multiplatform module |
| `tech.annexflow.lvm.quality` | `lvmLibs.plugins.blueprint.quality` | detekt |
| `tech.annexflow.lvm.publish` | `lvmLibs.plugins.blueprint.publish` | Publishing to Maven Central, the POM and ABI validation |
| `tech.annexflow.lvm.main-context` | `lvmLibs.plugins.blueprint.main.context` | The `@MainContext` compiler plugin |

The other plugins of the root project come from the same catalog: `lvmLibs.plugins.android.application`, `android.library`, `android.kmp.library`, `android.test`, `kotlin.multiplatform`, `kotlin.jvm`, `kotlin.compose`, `compose.multiplatform`, `detekt` and `vanniktech.mavenPublish`. A build can rename the catalog with `catalogName`; `options.catalogName` of `lvm report` has the name.

## Libraries and versions

The LVM version is set once, on the settings plugin in `settings.gradle.kts`. The catalog names LVM's libraries after their artifacts, so `lvmLibs.lvm.decompose` is `lvm-decompose`, and also lists the libraries and plugins that LVM is tested with, such as `lvmLibs.decompose` for Decompose itself. Prefer these entries to versions written in the build scripts. A build without the catalog aligns versions with `platform("tech.annexflow.lvm:lvm-bom:<version>")`.

## Source layout

A module uses the standard layout, `src/commonMain/kotlin/...`, unless `kotlin.flatLayout` is `true`. The flat layout uses shorter directories without the module's root package in the path:

| Directory | Source set |
|---|---|
| `src/` | `commonMain`, or `main` in an Android or Kotlin/JVM module |
| `test/` | `commonTest`, or the unit tests of an Android or Kotlin/JVM module |
| `src@<name>/` | `<name>Main`, such as `src@jvm/`, `src@ios/` or `src@android/`; a build type, flavor or variant, such as `src@debug/`; or another source set of a Kotlin/JVM module, such as `src@integrationTest/` |
| `test@<name>/` | `<name>Test`, such as `test@jvm/` |
| `androidTest/`, `androidTest@<name>/` | Instrumented tests of an Android module |

A module can't mix the layouts: with the flat layout, the build fails while `src/<sourceSet>/kotlin` still exists. Java files of a module without Kotlin Multiplatform go in the same directories as its Kotlin files. Resources, Android `res` and the manifest keep their usual directories. The `layout` of a module in `lvm report` says which one it uses; put new files where the module's existing files are.

With the flat layout, a Kotlin Multiplatform module keeps Java files in the directories of its JVM target, `src@jvm/` and `test@jvm/`, and of its Android target, `src@android/` and `test@androidHost/`. The Android target compiles them only after `withJava()` in `kotlin { android { } }`. The build fails on Java files in any other directory, such as `src/`.

## Secrets

Never write publishing or signing secrets, such as `mavenCentralPassword` or `signingInMemoryKey`, into the build's `gradle.properties` or any other file that git tracks. They belong in `~/.gradle/gradle.properties` or in the CI's secrets, as `ORG_GRADLE_PROJECT_*` variables. Never commit `local.properties`. `lvm check` reports both.

## After a change

After you change build scripts, `gradle.properties` or the layout, check the build with the `check` skill, then run the narrowest Gradle task that covers the change, such as `./gradlew :shared:build`. To add a module or create a build, use the `new` skill; to move the build to another LVM release, the `upgrade` skill.

## More

[reference.md](reference.md) covers Android applications, libraries and test modules, Kotlin/JVM modules, Compose, publishing, main context and the supported versions. For anything else, read LVM's README at the build's own version, `https://github.com/Lavmee/LVM-KMP/blob/v<version>/README.md`, with the version from `lvm report`; a build that tracks LVM's sources uses `main`.
