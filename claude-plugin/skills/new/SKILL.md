---
name: new
description: Creates a Gradle build with LVM's plugins with lvm new - a shared Kotlin Multiplatform module and an Android app that uses it - or adds a module to a build that uses LVM, set up the way its other modules are. Use when the user asks for a new project, app or build with LVM, or for a new module, feature module or library in a build that uses LVM.
argument-hint: "[directory or module path]"
allowed-tools:
  - Bash(lvm --version)
  - Bash(lvm new --help)
  - Bash(lvm report *)
  - Bash(lvm check --json *)
  - Bash(gh release list *)
---

# Create a build or a module with LVM

Arguments: `$ARGUMENTS`. They name the directory of a new build, or the Gradle path of a new module, such as `:feature:chat`. Run `lvm --version` first: it must print `lvm` and a version. If the command is missing, or is LVM2's `lvm` on Linux, follow "Read the build with lvm" in the `conventions` skill; a module can still be added by hand, as below, but a build needs the command.

Run `lvm report` in the target directory. When it finds a Gradle build there, or above it, add a module. Otherwise create a build.

## A new build

Agree on these with the user before creating anything, and suggest the defaults:

| Option | Flag | Default |
|---|---|---|
| Directory | the first argument | the current directory |
| `rootProject.name` | `--name` | the directory's name |
| Group and package of the modules | `--group` | `com.example`: always ask for the real one |
| Targets of `:shared` | `--targets` | `android,ios,jvm` |
| The Android app `:app` | `--no-app` leaves it out | on |
| `tech.annexflow.lvm.compose` | `--compose` | off |
| `tech.annexflow.lvm.quality` (detekt) | `--no-quality` leaves it out | on |
| Flat source layout | `--standard-layout` uses `src/<sourceSet>/kotlin` | on |

Then run the command with `--yes`. Without it, lvm opens a form that needs a terminal.

```bash
lvm new my-app --name my-app --group com.example.myapp --targets android,ios,jvm --compose --yes
```

`lvm new --help` lists the options of the installed lvm. The command lists the files it created: the settings script with LVM's settings plugin, the root build script with the plugin declarations, `gradle.properties`, `.gitignore`, the Gradle wrapper and the modules.

Then:

1. Run `lvm report` in the new build and compare `lvm.version` with the latest release, from `gh release list --repo Lavmee/LVM-KMP --exclude-pre-releases --limit 1`. lvm keeps the list of releases for a day, so right after a release it may create the build with the previous one; if so, offer to move it with the `upgrade` skill.
2. Run `lvm check --json` in the build and report what it finds, as the `check` skill describes. A missing Android SDK needs `ANDROID_HOME` or `sdk.dir` in `local.properties`.
3. Offer `git init` and a first commit, and `./gradlew build`. iOS targets build only on macOS with Xcode.

## A new module

Copy what the build already does. Don't apply LVM's defaults where the build does something else.

1. From `lvm report`, pick the existing modules that are most like the new one: the same kind (Kotlin Multiplatform library, Android application or library, Kotlin/JVM), and the same `plugins`. Read the build script of one of them; it is the template. Note the module's `layout`, `targets`, `group` or namespace, and how it declares dependencies.
2. Choose the Gradle path with the user, if the arguments don't give it. The directory follows the path: `:feature:chat` is `feature/chat`. Add the module to `settings.gradle.kts` the way the other modules are included.
3. Write `build.gradle.kts` from the template: the plugins as `lvmLibs` aliases (the build's catalog name is `options.catalogName` of `lvm report`), `lvm { targets { } }` for a Kotlin Multiplatform module, and dependencies from the catalog. A Kotlin Multiplatform library with an Android target needs a namespace: set `group` like the other modules, or `lvm { android { namespace = "..." } }`. An Android application sets `lvm { android { namespace } }` and `lvm { application { } }`.
4. If the module applies a plugin that the root `build.gradle.kts` doesn't declare yet, add the declaration with `apply false`.
5. Create the source directories of the module's layout with a first file. With the flat layout, common code goes in `src/`, tests in `test/` and platform code in `src@<name>/`, without the module's root package in the path: a file in the package `com.example.myapp.feature.chat` of `:feature:chat` goes directly in `src/`. With the standard layout, use `src/commonMain/kotlin/<package path>/`.
6. Run `lvm check --json` and fix what it finds, as the `check` skill describes. Then offer to build the module, such as `./gradlew :feature:chat:build`.

The `lvm` interface can also create a module: in its Modules tab, `n` asks for the module in a form and shows the files as a diff first. Mention it when the user prefers to do it themselves.
