# lvm-kmp

> **LVM — Lightweight Versatile Machinery.**
> `lvm-blueprint` — machinery that builds your app. `lvm-*` — machinery that runs inside it.

Kotlin Multiplatform libraries and Gradle convention plugins for Android, iOS and desktop apps.

> **Status:** early development, not published yet.

## What's inside

| Layer | What it is | Artifacts |
|---|---|---|
| **blueprint** | Build machinery: Gradle convention plugins | `lvm-blueprint-*` |
| **runtime** | App machinery: KMP libraries | `lvm-core`, `lvm-logger`, `lvm-storage`, `lvm-decompose*`, `lvm-main-context` |
| **versions** | Version catalog and BOM | `lvm-versions`, `lvm-bom` |

All artifacts are published to Maven Central under the `tech.annexflow.lvm` group and share a single version.

### Plugins

| Plugin ID | What it configures |
|---|---|
| `tech.annexflow.lvm.settings` | Settings plugin: the `lvmLibs` version catalog, repositories and build-wide defaults |
| `tech.annexflow.lvm.multiplatform` | Kotlin Multiplatform targets, source sets and compiler options |
| `tech.annexflow.lvm.compose` | Compose Multiplatform and the Compose compiler |
| `tech.annexflow.lvm.android.application` | Android application |
| `tech.annexflow.lvm.publish` | Maven Central publishing and ABI validation |
| `tech.annexflow.lvm.quality` | detekt |
| `tech.annexflow.lvm.main-context` | The `@MainContext` compiler plugin |

Each plugin does one thing. Apply only the ones you need.

### Libraries

| Artifact | Contents |
|---|---|
| `lvm-core` | Flow and coroutine utilities, `NetworkResult`, collection helpers |
| `lvm-logger` | Logger API, console logger, rotating file logger |
| `lvm-storage` | Multiplatform `DataStore` factory and serializers |
| `lvm-decompose` | Component infrastructure on top of [Decompose](https://github.com/arkivanov/Decompose): component context, component locals, scoped data, event bus, permissions, paging, state keeping |
| `lvm-decompose-compose` | Back handling, stack animations and a slot bottom sheet for Compose |
| `lvm-decompose-fetcher` | Cached, retrying network fetches inside components |
| `lvm-main-context` | `@MainContext`: runs the annotated function on the main dispatcher |
| `lvm-bom` | BOM for projects that don't use the version catalog |
| `lvm-versions` | Version catalog with every `lvm-*` artifact and the tested AGP, Kotlin, Compose and detekt versions |

## Getting started

### 1. Apply the settings plugin

The LVM version is set once, here. The plugin creates the `lvmLibs` version catalog and adds the `google()` and `mavenCentral()` repositories.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("tech.annexflow.lvm.settings") version "x.y.z"
}
```

### 2. Declare plugins in the root project

Declare every plugin in the root `build.gradle.kts` with `apply false`. Blueprint doesn't bundle AGP, Kotlin, Compose or detekt, so they must be on the build classpath, and all blueprint plugins must load in one classloader.

```kotlin
// build.gradle.kts
plugins {
    alias(lvmLibs.plugins.android.application) apply false
    alias(lvmLibs.plugins.android.kmp.library) apply false
    alias(lvmLibs.plugins.kotlin.multiplatform) apply false
    alias(lvmLibs.plugins.kotlin.compose) apply false
    alias(lvmLibs.plugins.compose.multiplatform) apply false
    alias(lvmLibs.plugins.detekt) apply false

    alias(lvmLibs.plugins.blueprint.multiplatform) apply false
    alias(lvmLibs.plugins.blueprint.compose) apply false
    alias(lvmLibs.plugins.blueprint.android.application) apply false
    alias(lvmLibs.plugins.blueprint.publish) apply false
    alias(lvmLibs.plugins.blueprint.quality) apply false
    alias(lvmLibs.plugins.blueprint.main.context) apply false
}
```

### 3. Apply plugins in a module

```kotlin
// shared/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
    alias(lvmLibs.plugins.blueprint.compose)
}

lvm {
    targets {
        android()
        ios()
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(lvmLibs.decompose)
        implementation(lvmLibs.logger)
    }
}
```

## Configuration

Every value blueprint sets has a default and can be changed. Values are resolved from four places, weakest first:

1. Blueprint defaults.
2. `lvm { }` in `settings.gradle.kts`: build-wide defaults.
3. `lvm.*` Gradle properties in `gradle.properties` or `-P`.
4. `lvm { }` in a module's build script.

```kotlin
// settings.gradle.kts
lvm {
    android { minSdk = 26 }
    jvm { target = 21 }
    kotlin { optIns.add("kotlin.time.ExperimentalTime") }
}
```

```properties
# gradle.properties
lvm.kotlin.warningsAsErrors=true
```

```kotlin
// feature/build.gradle.kts
lvm {
    targets {
        android()
        ios { framework { baseName = "FeatureKit"; isStatic = true } }
    }
    quality { rules(project(":detekt-rules")) }
}
```

Every automatic behavior can be switched off, including the dependencies blueprint adds and the catalog and repositories added by the settings plugin. Anything `lvm { }` doesn't cover is configured with the regular `android { }` and `kotlin { }` blocks. For the Android target of a library, `kotlin { android { } }` is available only when the module also lists the AGP plugin; see [Android](#android).

## Android

- **Applications** use `tech.annexflow.lvm.android.application`.
- **Libraries** get their Android target from `lvm { targets { android() } }`, which applies `com.android.kotlin.multiplatform.library`. This requires AGP 9.4 or newer.
  The namespace defaults to the module's `group` followed by its path (`com.example` and `:feature:user-profile` give `com.example.feature.user_profile`), so library modules should set `group` or `lvm { android { namespace = "…" } }`.
  Modules that configure the Android target with the native `kotlin { android { } }` DSL also add `alias(lvmLibs.plugins.android.kmp.library)` to their `plugins { }`; lvm then reuses the applied plugin, and values set natively win over `lvm { android { } }`.

## Supported versions

LVM requires Gradle 9.6, AGP 9.4 and Kotlin 2.4 or newer. Your project chooses its Gradle, AGP, Kotlin, Compose and detekt versions. LVM is tested with the versions listed in `lvm-versions` and with the Gradle version of its own wrapper.

Blueprint checks the Gradle, AGP and Kotlin versions: a version below the minimum fails the build with a clear error, and a version newer than tested produces a warning. For Gradle, that warning comes only from `tech.annexflow.lvm.settings`, once per build; without the settings plugin, the project plugins still enforce the minimum. Compose and detekt versions are not checked.

One exception to choosing your own Kotlin version is `tech.annexflow.lvm.main-context`: a compiler plugin works only with the Kotlin version it was built for, so projects that apply it must use the Kotlin version from `lvm-versions`.

## Not included

- **A design system or UI components.** Theming is too app-specific to share.
- **Log encryption and log sharing.** `lvm-logger` writes logs and exposes the log directory; encrypting and sending them is up to the app.
- **Dependency injection.** LVM uses no DI annotations and works with any DI framework.

## Documentation

- [Releasing](RELEASING.md) — how releases are published

## License

[Apache License 2.0](LICENSE)
