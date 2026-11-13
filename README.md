# LVM-KMP

> **LVM: Lightweight Versatile Machinery.**
> `lvm-blueprint` is the machinery that builds your app. `lvm-*` is the machinery that runs inside it.

Kotlin Multiplatform libraries and Gradle convention plugins for Android, iOS and desktop apps.

> **Status:** early development, not published yet.

## What's inside

| Layer | What it is | Artifacts |
|---|---|---|
| **blueprint** | Build machinery: Gradle convention plugins | `lvm-blueprint-*` |
| **runtime** | App machinery: KMP libraries | `lvm-core`, `lvm-coroutines`, `lvm-io`, `lvm-logger*`, `lvm-storage`, `lvm-decompose*`, `lvm-main-context` |
| **compiler** | Kotlin compiler plugins | `lvm-main-context-compiler` |
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
| `tech.annexflow.lvm.main-context` | The `@MainContext` compiler plugin; see [Main context](#main-context) |

Each plugin does one thing. Apply only the ones you need.

### Libraries

| Artifact | Contents |
|---|---|
| `lvm-core` | `Loadable`, `EvictingMap`, collection, string and function helpers |
| `lvm-coroutines` | `StateFlow` without a scope (`mapState`, `combineState`), Flow helpers, `runSuspendCatching`, `StateFlow` helpers for `Loadable` |
| `lvm-io` | `AppDirectories`: the app's cache and data directories on Android, iOS and JVM |
| `lvm-logger` | Logger API: `Logger`, `Severity`, `LoggerHolder`, combining loggers with `+` |
| `lvm-logger-console` | Console logger: Logcat on Android, the system log on Apple platforms, the console on JVM |
| `lvm-logger-file` | Rolling file logger |
| `lvm-storage` | `createDataStore`: a `DataStore` in the app's data directory or at any path, with serializers for JSON and kotlinx-serialization's binary formats |
| `lvm-decompose` | `AppComponentContext` on top of [Decompose](https://github.com/arkivanov/Decompose): component locals, a coroutine scope per component, instances shared by the component tree, `EventBus`, a context for previews and tests, state keeping and paging |
| `lvm-decompose-permissions` | moko-permissions' `PermissionsController` for components, kept across activity recreation on Android. Android and iOS only |
| `lvm-decompose-compose` | Back handling, stack animations and a slot bottom sheet for Compose |
| `lvm-decompose-fetcher` | `Fetcher`: network reads for components through the app's Ktor `HttpClient`, with a file cache that serves the last responses offline and states that retry a read |
| `lvm-main-context` | `@MainContext`: calls to the annotated function from coroutines run on the main thread |
| `lvm-bom` | BOM for projects that don't use the version catalog |
| `lvm-versions` | Version catalog with every `lvm-*` artifact and the tested AGP, Kotlin, Compose and detekt versions |

`fileLogger` from `lvm-logger-file` takes its directory as a lambda that runs on the writer's thread, for example `fileLogger("app") { Path(appDirectories.cache, "logs") }` with `AppDirectories` from `lvm-io`.

`createDataStore` from `lvm-storage` returns androidx's `DataStore` with its file in the data directory of `AppDirectories`, for example `createDataStore(appDirectories, "settings.json", jsonSerializer { Settings() })`. `jsonSerializer` and `binarySerializer` store `@Serializable` values, so the module that declares them applies the Kotlin serialization plugin. A file that can't be decoded is replaced with the default value, unless the store gets another `corruptionHandler`.

`AppComponentContext` from `lvm-decompose` is the component context of an app. A component delegates to it, as in `class ProfileComponent(context: AppComponentContext) : AppComponentContext by context`, and gets component locals, values that a parent passes down to its subtree like Compose's `CompositionLocal` (`provide(AnalyticsLocal, analytics)` in the parent, `val analytics by AnalyticsLocal` in a child); `componentScope`, which runs on the main thread and is cancelled with the component; instances that the whole tree shares, such as an `EventBus`; and a logger. `PreviewAppComponentContext` runs a component in previews and tests, without a UI.

`lvm-decompose` also keeps a component's state across process death with `mutableState { ... }`, gives lists a `paging(flow)` that shows the last page when a screen comes back, and gives previews `stubPaging(items)` and `loadedPagingFlow(items)`. Its `DecomposeNavigationHandler` and `replaceAll` are `@MainContext` functions, so in a module that applies `tech.annexflow.lvm.main-context` their calls from coroutines run on the main thread; import `tech.annexflow.lvm.decompose.replaceAll`, not Decompose's function of the same name. `lvm-decompose-permissions` passes moko-permissions' `PermissionsController` to components through the component local `LocalPermissionsController`, and on Android `retainedPermissionsController()` keeps it across activity recreation.

Every component tree starts from a root context: `retainedAppComponent(logger) { context -> ... }` or `defaultAppComponent(logger)` in an Android activity, `AppComponentRoot` on iOS, and `rootAppComponentContext(componentContext, logger)` on JVM, for example on a `DefaultComponentContext` with a `LifecycleRegistry`. [iOS](docs/ios.md) shows how an iOS app builds its root, saves its state and forwards back gestures.

`lvm-decompose-compose` connects Decompose components with Compose. `BackHandler(backHandler) { ... }` handles back with a component's `backHandler` while it is in the composition. A dialog or a bottom sheet takes the back gestures of its window, so a component shown in one handles back with `rememberWindowBackHandler()` instead: back reaches the component while it handles back, and closes the dialog otherwise. `stackAnimation { ... }` animates Decompose's `ChildStack` with an animator for each child that enters or leaves. Its selector reads the change from a `SelectorContext`, for example with `animate<SettingsComponent>(child = { ... }, other = { ... })`, and returns an animator such as `slide(invert = false)`, `verticalSlide()`, `slowFade()` or `none()`. Like Decompose's stack animations, they need an opt-in to `ExperimentalDecomposeApi`.

`ChildSlotModalBottomSheet(slot, onDismiss = { navigation.dismiss() }) { child -> ... }` shows the child of a Decompose `ChildSlot` in compose-unstyled's modal bottom sheet. The sheet opens when the slot gets a child and closes when the slot is empty again; when the user hides it, by dragging it down, tapping outside of it or pressing back, `onDismiss` receives the child. `drag = SheetDrag.Handle` lets the sheet drag only from parts of its content marked with `Modifier.sheetDragHandle()`, `windowContent` runs inside the sheet's window, and `overlay` draws a `Scrim` under the sheet.

`lvm-decompose-fetcher` reads data for components through the app's Ktor `HttpClient`. `installFetcherCache(directory, isOffline)` in the client's configuration keeps responses in a directory, for example `Path(appDirectories.cache, "fetcher")`, and serves them while `isOffline()` returns `true`. The root provides `Fetcher(httpClient)` with `provideLocals(LocalFetcher provides ...)`, and a component reads it as `fetcher`: `fetcherResultState { fetcher.get(url).body<Rows>() }` retries the read and holds its result as a `Loadable`.

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

A stronger scalar value replaces a weaker one. List values differ by setting: `kotlin.optIns`, `kotlin.freeCompilerArgs`, `quality.config` and `compose.stabilityConfigurationFiles` set in a module are appended to the build-wide list, while `ios.architectures`, `quality.source` and `mainContext.platforms` set in a module replace it. As Gradle properties, list values are comma-separated, so a single value can't contain a comma.

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

### Source layout

Modules use the standard Kotlin Multiplatform layout, `src/commonMain/kotlin/...`, unless `lvm { kotlin { flatLayout = true } }` or `lvm.kotlin.flatLayout=true` turns on the flat layout. It uses shorter directories and leaves the module's root package out of them:

| Directory | Source set |
|---|---|
| `src/` | `commonMain` |
| `test/` | `commonTest` |
| `src@<name>/` | `<name>Main`, for example `src@jvm/`, `src@ios/` or `src@android/` |
| `test@<name>/` | `<name>Test`, for example `test@jvm/` or `test@androidHost/` |

With the flat layout, the quality plugin analyzes all of these directories, turns off detekt's `InvalidPackageDeclaration` and treats `test@<name>/` as test code; the build's own detekt config files can override both. A module can't mix the two layouts: blueprint fails if it still has `src/<sourceSet>/kotlin`. Resources, Android `res` and the manifest keep their conventional directories, and a build-wide `lvm.quality.source` replaces the flat default entirely.

### Settings plugin options

| Setting | Default | Effect |
|---|---|---|
| `catalog` | `true` | Creates a version catalog from `lvm-versions` |
| `catalogName` | `lvmLibs` | Name of that catalog. It can't be `lvm`: every catalog is also a project extension, and `lvm` is the `lvm { }` block |
| `catalogVersion` | the plugin's own version | Version of `lvm-versions` to import |
| `repositories` | `true` | Adds `google()` and `mavenCentral()` to `dependencyResolutionManagement` |
| `values` | empty | Raw build-wide values by key, such as `values.put("android.minSdk", "26")`. The typed sections win over these |

```kotlin
// settings.gradle.kts
lvm {
    catalogName = "lvmCatalog"
    repositories = false
}
```

## Android

- **Applications** use `tech.annexflow.lvm.android.application`.
- **Libraries** get their Android target from `lvm { targets { android() } }`, which applies `com.android.kotlin.multiplatform.library`. This requires AGP 9.4 or newer.
  The namespace defaults to the module's `group` followed by its path (`com.example` and `:feature:user-profile` give `com.example.feature.user_profile`), so library modules should set `group` or `lvm { android { namespace = "..." } }`.
  Modules that configure the Android target with the native `kotlin { android { } }` DSL also add `alias(lvmLibs.plugins.android.kmp.library)` to their `plugins { }`; lvm then reuses the applied plugin, and values set natively win over `lvm { android { } }`.

## Compose

`tech.annexflow.lvm.compose` sets up a Kotlin Multiplatform module for Compose: it applies Compose Multiplatform (`org.jetbrains.compose`) and the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`), and configures the compiler's metrics, reports and stability configuration files. It adds no dependencies; a module declares the Compose libraries it uses, which `lvm-versions` lists at the tested version.

```kotlin
// feature/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
    alias(lvmLibs.plugins.blueprint.compose)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(lvmLibs.compose.foundation)
    }
}
```

| Setting | Default | Effect |
|---|---|---|
| `metrics` | `false` | Writes the compiler's metrics into `metricsDirectory`, by default `build/compose-metrics` of the module |
| `reports` | `false` | Writes the compiler's reports, such as the stability of each class, into `reportsDirectory`, by default `build/compose-reports` of the module |
| `stabilityConfigurationFiles` | empty | Stability configuration files of the compiler, relative to the root project directory |

```properties
# gradle.properties
lvm.compose.reports=true
```

In a module that also applies `tech.annexflow.lvm.quality`, the detekt Compose rules are on by default, and detekt accepts composable functions named like types. `lvm { quality { composeRules = false } }` turns the rules off.

## Main context

`tech.annexflow.lvm.main-context` lets code mark functions that must run on the main thread, such as navigation. In a module that applies it, every call to such a function from a coroutine runs on `Dispatchers.Main.immediate`.

```kotlin
// shared/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.multiplatform)
    alias(lvmLibs.plugins.blueprint.main.context)
}
```

```kotlin
class Navigator {
    @MainContext
    fun push(screen: Screen) { ... }
}

suspend fun open(id: Id) {
    val screen = repository.load(id)
    navigator.push(screen) // runs on the main thread
}
```

The plugin adds `lvm-main-context`, which holds the annotation, and runs the compiler plugin `lvm-main-context-compiler` of the same version. The compiler plugin moves each call, together with its receiver and arguments, into `withContext(Dispatchers.Main.immediate)`. A call made on the main thread runs at once.

- Calls move from suspend functions, suspend lambdas and lambdas passed to inline functions there, unless the parameter is `crossinline` or `noinline`. Calls from other code, calls through function references and calls in default parameter values run where they are made.
- Receivers and arguments are computed on the main thread too, so compute expensive ones before the call, as `open` does.
- Overrides of an annotated function inherit the annotation, also from other modules.
- A call whose receiver or arguments `return`, `break` or `continue` out of it, as in `push(screen ?: return)`, is a compile error. Compute the value in a variable first.

`Dispatchers.Main` comes from `kotlinx-coroutines-android` on Android, which `lvm-main-context` depends on, and from `kotlinx-coroutines-core` on Apple platforms. On JVM it needs `kotlinx-coroutines-swing` or `kotlinx-coroutines-javafx`; Compose Desktop adds the first. Without a main dispatcher, a moved call fails with an `IllegalStateException`. Tests set one with `Dispatchers.setMain` from `kotlinx-coroutines-test`.

| Setting | Default | Effect |
|---|---|---|
| `platforms` | `android`, `ios`, `jvm` | Platforms whose main and test compilations run the compiler plugin. An empty list turns it off |
| `dependency` | `true` | Adds `lvm-main-context` to `commonMain` |

```kotlin
// feature/build.gradle.kts
lvm {
    mainContext { platforms.set(listOf("android", "ios")) }
}
```

The compiler plugin works only with the Kotlin version it was built for; see [Supported versions](#supported-versions).

## Supported versions

LVM requires Gradle 9.6, AGP 9.4 and Kotlin 2.4 or newer. Your project chooses its Gradle, AGP, Kotlin, Compose and detekt versions. LVM is tested with the versions listed in `lvm-versions` and with the Gradle version of its own wrapper.

The minimums are strict on purpose and will stay that way. They follow recent Gradle, AGP and Kotlin releases and move up whenever LVM moves to newer tools; older toolchains are not kept alive. If your project stays on older versions, stay on the LVM release that supports them.

Blueprint checks the Gradle, AGP and Kotlin versions: a version below the minimum fails the build with a clear error, and a version newer than tested produces a warning. For Gradle, that warning comes only from `tech.annexflow.lvm.settings`, once per build; without the settings plugin, the project plugins still enforce the minimum. Compose and detekt versions are not checked.

One exception to choosing your own Kotlin version is `tech.annexflow.lvm.main-context`: a compiler plugin works only with the Kotlin version it was built for, so projects that apply it must use the Kotlin version from `lvm-versions`. Any other version, a patch release included, fails the build.

## Documentation

- [Releasing](RELEASING.md): how releases are published
- [iOS](docs/ios.md): how an iOS app runs a component tree of `lvm-decompose`

## License

[Apache License 2.0](LICENSE)
