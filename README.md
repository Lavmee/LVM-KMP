# LVM-KMP

> **LVM: Lightweight Versatile Machinery.**
> `lvm-blueprint` is the machinery that builds your app. `lvm-*` is the machinery that runs inside it.

Kotlin Multiplatform libraries and Gradle convention plugins for Android, iOS and desktop apps.

> **Status:** early development. The latest release is 0.5.0.

## What's inside

| Layer | What it is | Artifacts |
|---|---|---|
| **blueprint** | Build machinery: Gradle convention plugins | `lvm-blueprint-*` |
| **runtime** | App machinery: KMP libraries | `lvm-core`, `lvm-coroutines`, `lvm-io`, `lvm-logger*`, `lvm-storage`, `lvm-decompose*`, `lvm-main-context` |
| **compiler** | Kotlin compiler plugins | `lvm-main-context-compiler` |
| **versions** | Version catalog and BOM | `lvm-versions`, `lvm-bom` |
| **tui** | `lvm`, a terminal app for builds that use LVM; see [The lvm command](docs/tui.md) | The `lvm` command, in GitHub Releases and as `lvm-tui` on crates.io |

All artifacts are published to Maven Central under the `tech.annexflow.lvm` group and share a single version. The `lvm` command has the same version: it comes with each GitHub Release and as the crate `lvm-tui` on crates.io.

### Plugins

| Plugin ID | What it configures |
|---|---|
| `tech.annexflow.lvm.settings` | Settings plugin: the `lvmLibs` version catalog, repositories and build-wide defaults |
| `tech.annexflow.lvm.multiplatform` | Kotlin Multiplatform targets, source sets and compiler options |
| `tech.annexflow.lvm.jvm` | Kotlin/JVM: Java version, compiler options, `kotlin-test` and the flat layout |
| `tech.annexflow.lvm.compose` | The Compose compiler, and Compose Multiplatform in Kotlin Multiplatform modules |
| `tech.annexflow.lvm.android.application` | Android application: SDK levels, Java version, release minification and desugaring |
| `tech.annexflow.lvm.android.library` | Android library without Kotlin Multiplatform: SDK levels, Java version, flavor source sets, desugaring and consumer keep rules |
| `tech.annexflow.lvm.android.test` | Android test module, such as a macrobenchmark or a baseline profile generator: SDK levels, Java version and the runner of its tests |
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
| `lvm-decompose-compose` | Back handling, stack animations, and a bottom sheet, an overlay and plain content for a slot's child in Compose |
| `lvm-decompose-fetcher` | `Fetcher`: network reads for components through the app's Ktor `HttpClient`, with a file cache that serves the last responses offline and states that retry a read and can be refreshed |
| `lvm-main-context` | `@MainContext`: calls to the annotated function from coroutines run on the main thread |
| `lvm-bom` | BOM for projects that don't use the version catalog |
| `lvm-versions` | Version catalog with every `lvm-*` artifact and the tested AGP, Kotlin, Compose and detekt versions |

`fileLogger` from `lvm-logger-file` takes its directory as a lambda that runs on the writer's thread, for example `fileLogger("app") { Path(appDirectories.cache, "logs") }` with `AppDirectories` from `lvm-io`. Logging doesn't wait for the disk: the writer buffers up to 64 lines, and a log call waits only when the buffer is full. When a write fails, lines are dropped until the file can be written again, and a deleted directory is created again.

`createDataStore` from `lvm-storage` returns androidx's `DataStore` with its file in the data directory of `AppDirectories`, for example `createDataStore(appDirectories, "settings.json", jsonSerializer { Settings() })`. `jsonSerializer` and `binarySerializer` store `@Serializable` values, so the module that declares them applies the Kotlin serialization plugin. A file that can't be decoded is replaced with the default value, unless the store gets another `corruptionHandler`.

`AppComponentContext` from `lvm-decompose` is the component context of an app. A component delegates to it, as in `class ProfileComponent(context: AppComponentContext) : AppComponentContext by context`, and gets component locals, values that a parent passes down to its subtree like Compose's `CompositionLocal` (`provide(AnalyticsLocal, analytics)` in the parent, `val analytics by AnalyticsLocal` in a child); `componentScope`, which runs on the main thread and is cancelled with the component; instances that the whole tree shares, such as an `EventBus`; and a logger. `PreviewAppComponentContext` runs a component in previews and tests, without a UI.

The root shares an instance with the whole tree through `getOrCreateScoped { ... }`, and any component reads it with `getScoped<Type>()`. The key is the type argument as written, so an instance created as `MutableStateFlow<Settings>` is not found as `StateFlow<Settings>`. A `StateFlow` that components read without knowing how the root built it is shared by its value type instead. The root keeps the flow it creates, as in `val settings = getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }`, and a component in another module reads it as `val settings: StateFlow<Settings> get() = getScopedStateFlow()`. The value type is the one of the flow that the factory returns, so `getOrCreateScopedStateFlow { MutableStateFlow(Session.SignedIn(user)) }` shares a flow of `Session.SignedIn`, and `getOrCreateScopedStateFlow<Session, _> { ... }` shares it as a flow of `Session`.

`lvm-decompose` also keeps a component's state across process death with `mutableState { ... }`, gives lists a `paging(flow)` that shows the last page when a screen comes back, and gives previews `stubPaging(items)` and `loadedPagingFlow(items)`. Its `DecomposeNavigationHandler` and `replaceAll` are `@MainContext` functions, so in a module that applies `tech.annexflow.lvm.main-context` their calls from coroutines run on the main thread; import `tech.annexflow.lvm.decompose.replaceAll`, not Decompose's function of the same name. `lvm-decompose-permissions` passes moko-permissions' `PermissionsController` to components through the component local `LocalPermissionsController`, and on Android `retainedPermissionsController()` keeps it across activity recreation. On Android, `openAppNotificationSettings(context)` and `openAppLocaleSettings(context)` on the controller open the system settings of the app's notifications and language, and open the app's settings instead where the system has no such screen.

Every component tree starts from a root context: `retainedAppComponent(logger) { context -> ... }` or `defaultAppComponent(logger)` in an Android activity, `AppComponentRoot` on iOS, and `rootAppComponentContext(componentContext, logger)` on JVM, for example on a `DefaultComponentContext` with a `LifecycleRegistry`. [iOS](docs/ios.md) shows how an iOS app builds its root, saves its state and forwards back gestures.

`lvm-decompose-compose` connects Decompose components with Compose. `BackHandler(backHandler) { ... }` handles back with a component's `backHandler` while it is in the composition. A dialog or a bottom sheet takes the back gestures of its window, so a component shown in one handles back with `rememberWindowBackHandler()` instead: back reaches the component while it handles back, and closes the dialog otherwise. `stackAnimation { ... }` animates Decompose's `ChildStack` with an animator for each child that enters or leaves. Its selector reads the change from a `SelectorContext`, for example with `animate<SettingsComponent>(child = { ... }, other = { ... })` by the type of one screen, or with `animate<HistoryComponent, ChatComponent>(child = { ... }, other = { ... })` for a change between two types of screens, and returns an animator such as `slide(invert = false)`, `verticalSlide()`, `slowFade()` or `none()`. Like Decompose's stack animations, they need an opt-in to `ExperimentalDecomposeApi`.

`ChildSlotModalBottomSheet(slot, onDismiss = { navigation.dismiss() }) { child -> ... }` shows the child of a Decompose `ChildSlot` in compose-unstyled's modal bottom sheet. The sheet opens when the slot gets a child and closes when the slot is empty again; when the user hides it, by dragging it down, tapping outside of it or pressing back, `onDismiss` receives the child. `drag = SheetDrag.Handle` lets the sheet drag only from parts of its content marked with `Modifier.sheetDragHandle()`, `windowContent` runs inside the sheet's window, and `overlay` draws a `Scrim` under the sheet.

`ChildSlotOverlay(slot) { child -> ... }` shows the child over the screen. The child slides up from the bottom when the slot gets a child and slides back down when the slot is empty again, and input is blocked while it moves. `transitionSpec` replaces the slide. The animation moves only what is drawn in the same window, so a child that opens a window of its own, such as an `AlertDialog`, goes into `ChildSlotContent(slot) { child -> ... }` instead, which shows the child while the slot has one, without animation.

On Android, back gestures, predictive back included, reach Compose through Google's `androidx.navigationevent`. `lvm-decompose-compose` asks for the version that androidx.activity depends on, so an app keeps the version that its own androidx.activity brings. Components get back through essenty's `BackHandler`, as in Decompose, and `lvm-decompose` depends on androidx.activity. On JVM and iOS, Compose Multiplatform brings JetBrains' build of navigationevent. The versions LVM is tested with are those in `lvm-versions`.

`lvm-decompose-fetcher` reads data for components through the app's Ktor `HttpClient`. `installFetcherCache(directory, isOffline)` in the client's configuration keeps responses in a directory, for example `Path(appDirectories.cache, "fetcher")`, and serves them while `isOffline()` returns `true`. The root provides `Fetcher(httpClient)` with `provideLocals(LocalFetcher provides ...)`, and a component reads it as `fetcher`: `fetcherResultState { fetcher.get(url).body<Rows>() }` retries the read and holds its result as a `Loadable`. The state is a `FetcherStateFlow`, and its `refresh()` reads again: the current value stays until the new result replaces it, so a pull-to-refresh keeps the data on screen. The state also refreshes on every emission of the flow passed as `refreshOn`, and on every emission of `retryOn` while it is `Failed`. Pass events to `retryOn`, such as the connection coming back, not a state such as "is connected".

A failed read is retried with `Fetcher.retryPolicy` by default: ten attempts, a second apart. An app sets its own policy once, at the root of its component tree, next to the fetcher: `provideLocals(LocalFetcher provides Fetcher(httpClient), LocalFetcherRetryPolicy provides policy)`, with a policy of kotlin-retry such as `stopAtAttempts<Throwable>(4) + equalJitterBackoff(min = 1_000, max = 30_000)`. A parent can give its subtree another policy the same way, and a `retryPolicy` argument wins over both. Previews and tests get `Fetcher.retryPolicy` without setup. The policy of the tree reaches every call in a component, because a component is an `AppComponentContext`: `fetcherState` and `fetcherResultState` have overloads on `AppComponentContext` that read `LocalFetcherRetryPolicy`, and Kotlin picks them over those on `CoroutineScopeOwner` for their more specific receiver. Code whose receiver is only a `CoroutineScopeOwner` gets `Fetcher.retryPolicy`.

A `Fetcher` shares requests. While a request for a url is running, another `get` of that url waits for it instead of sending its own, and every caller gets the same `ResponseBody`, which each of them can read. When the components of a tree all ask for the same document at startup, the app sends one request, and retries that overlap share their requests the same way. A failed request fails every caller that waits for it, and a request that finished, failed or was cancelled is not reused. Requests run on `Dispatchers.IO` in a scope of the fetcher, so cancelling one caller doesn't cancel the request for the others. A request is cancelled when no caller waits for it any more, and closing the `HttpClient` cancels the running requests and makes every later `get` fail. The fetchers of separate `Fetcher(httpClient)` calls share nothing, so a tree provides one fetcher at its root.

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
    alias(lvmLibs.plugins.android.library) apply false
    alias(lvmLibs.plugins.android.kmp.library) apply false
    alias(lvmLibs.plugins.android.test) apply false
    alias(lvmLibs.plugins.kotlin.multiplatform) apply false
    alias(lvmLibs.plugins.kotlin.jvm) apply false
    alias(lvmLibs.plugins.kotlin.compose) apply false
    alias(lvmLibs.plugins.compose.multiplatform) apply false
    alias(lvmLibs.plugins.detekt) apply false
    alias(lvmLibs.plugins.vanniktech.mavenPublish) apply false

    alias(lvmLibs.plugins.blueprint.multiplatform) apply false
    alias(lvmLibs.plugins.blueprint.jvm) apply false
    alias(lvmLibs.plugins.blueprint.compose) apply false
    alias(lvmLibs.plugins.blueprint.android.application) apply false
    alias(lvmLibs.plugins.blueprint.android.library) apply false
    alias(lvmLibs.plugins.blueprint.android.test) apply false
    alias(lvmLibs.plugins.blueprint.publish) apply false
    alias(lvmLibs.plugins.blueprint.quality) apply false
    alias(lvmLibs.plugins.blueprint.main.context) apply false
}
```

`compose.multiplatform` is needed only by Kotlin Multiplatform modules that apply `tech.annexflow.lvm.compose`. A build whose Compose code is only in Android modules without Kotlin Multiplatform, such as an app on Jetpack Compose, leaves it out. List only the plugins your modules use: a build without Kotlin/JVM modules leaves out `kotlin.jvm` and `blueprint.jvm`, and one without test modules leaves out `android.test` and `blueprint.android.test`.

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
        implementation(lvmLibs.lvm.decompose)
        implementation(lvmLibs.lvm.logger)
    }
}
```

In `lvmLibs`, LVM's libraries are named after their artifacts (`lvmLibs.lvm.decompose` is `lvm-decompose`) and blueprint's plugins `blueprint-<name>` (`lvmLibs.plugins.blueprint.multiplatform`). The other entries are the libraries and plugins LVM is tested with, such as `lvmLibs.decompose` for Decompose itself. A project without the catalog aligns LVM's versions with `lvm-bom`: `implementation(platform("tech.annexflow.lvm:lvm-bom:x.y.z"))`, then `implementation("tech.annexflow.lvm:lvm-core")` without a version.

## Configuration

Every value blueprint sets has a default and can be changed. Values are resolved from four places, weakest first:

1. Blueprint defaults.
2. `lvm { }` in `settings.gradle.kts`: build-wide defaults.
3. `lvm.*` Gradle properties in `gradle.properties` or `-P`.
4. `lvm { }` in a module's build script.

A stronger scalar value replaces a weaker one. List values differ by setting: `kotlin.optIns`, `kotlin.freeCompilerArgs`, `quality.config`, `compose.stabilityConfigurationFiles`, `application.proguardFiles`, `library.consumerProguardFiles` and `library.resourcePrefixDroppedSegments` set in a module are appended to the build-wide list, while `ios.architectures`, `quality.source` and `mainContext.platforms` set in a module replace it. As Gradle properties, list values are comma-separated, so a single value can't contain a comma. A build-wide empty list replaces the default, so `application { proguardFiles = emptyList() }` in `settings.gradle.kts` or `lvm.application.proguardFiles=` in `gradle.properties` leaves out `proguard-rules.pro`, and `library { consumerProguardFiles = emptyList() }` leaves out `consumer-rules.pro`; an empty list in a module that appends adds nothing.

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

Your own convention plugins can read the build-wide values and configure the `lvm { }` sections of a module; see [Build logic](docs/build-logic.md).

### Kotlin and JVM

`lvm { jvm { } }` and `lvm { kotlin { } }` hold the values that every plugin with Kotlin code uses: the multiplatform plugin, the JVM plugin and the Android plugins.

| Setting | Default | Effect |
|---|---|---|
| `jvm.target` | `21` | Bytecode version of JVM and Android compilations, for Kotlin and Java |
| `kotlin.warningsAsErrors` | `false` | Turns compiler warnings into errors |
| `kotlin.optIns` | empty | Opt-ins of every compilation, such as `kotlin.time.ExperimentalTime` |
| `kotlin.freeCompilerArgs` | empty | Further arguments of the Kotlin compiler |
| `kotlin.explicitApi` | `false` | Strict explicit API mode for the main code of libraries and Kotlin/JVM modules; tests keep default visibility |
| `kotlin.testDependencies` | `true` | Adds `kotlin-test` to tests: to `commonTest` in Kotlin Multiplatform modules, and to the unit tests of Android and Kotlin/JVM modules |
| `kotlin.flatLayout` | `false` | The flat source layout; see [Source layout](#source-layout) |
| `kotlin.jvmDefault` | unset | How Kotlin compiles interface functions with a body for JVM and Android, as its `-jvm-default`: `enable`, `no-compatibility` or `disable`. While it is unset, Kotlin's own default applies |

### Source layout

Modules use the standard Kotlin Multiplatform layout, `src/commonMain/kotlin/...`, unless `lvm { kotlin { flatLayout = true } }` or `lvm.kotlin.flatLayout=true` turns on the flat layout. It uses shorter directories and leaves the module's root package out of them:

| Directory | Source set |
|---|---|
| `src/` | `commonMain` |
| `test/` | `commonTest` |
| `src@<name>/` | `<name>Main`, for example `src@jvm/`, `src@ios/` or `src@android/` |
| `test@<name>/` | `<name>Test`, for example `test@jvm/` or `test@androidHost/` |

Java files go next to the Kotlin files of a JVM target: `src@jvm/NativeMarkdown.java` compiles into `jvmMain` next to `src@jvm/Markdown.kt`, and `test@jvm/` holds the Java files of its tests. The Android target compiles Java only after `withJava()` in `kotlin { android { } }`, and then from `src@android/`, `test@androidHost/` and `test@androidDevice/`. Kotlin compiles against Java files in other directories, such as `src/` or `src@ios/`, but nothing compiles them into the build, so blueprint fails and names them.

With the flat layout, the quality plugin analyzes all of these directories, turns off detekt's `InvalidPackageDeclaration` and treats `test@<name>/` and `androidTest@<name>/` as test code; the build's own detekt config files can override both. A module can't mix the two layouts: blueprint fails if it still has `src/<sourceSet>/kotlin`. Resources, Android `res` and the manifest keep their conventional directories, and a build-wide `lvm.quality.source` replaces the flat default entirely.

An Android application or an Android library follows the same switch for its Kotlin sources: `src/` for `main`, `test/` for unit tests, `androidTest/` for instrumented tests and `testFixtures/` for test fixtures, each followed by `@<name>` for a build type, a flavor or a variant, as in `src@debug/`, `src@paid/`, `test@debug/` or `androidTest@freeDebug/`. Java sources go in the same directories as the Kotlin sources, so `src/NativeMarkdown.java` compiles into `main` next to `src/Markdown.kt`. With the flat layout, an application or a library fails while it still has `src/<sourceSet>/kotlin` or `src/<sourceSet>/java`.

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

### Report

Every module with an LVM plugin has the task `lvmReport`, which writes what the plugins set up in the module as JSON, for tools that show or check a build, such as the [`lvm` command](docs/tui.md). Run it for the whole build, then read the reports from one directory of the root project:

```bash
./gradlew lvmReport
```

Each module's report is `build/lvm/report/<path>/project.json`, where `<path>` is the module's path with `/` for `:`, so `:feature:chat` writes `build/lvm/report/feature/chat/project.json`. It holds the LVM plugins the module applies, its targets, the versions of Gradle, Kotlin and AGP with the minimum and tested ones, the options of the settings plugin, the declared project and LVM dependencies, the source sets and the tasks. Every value of `lvm { }` that the module uses comes with its source: `default`, `settings` for the `lvm { }` block of `settings.gradle.kts`, `property` for a `lvm.*` Gradle property or `module` for the module's own `lvm { }`. The field `schema` changes when a field changes its meaning.

## Android

- **Applications** use `tech.annexflow.lvm.android.application`; see [Applications](#applications).
- **Android libraries without Kotlin Multiplatform**, for example ones with product flavors, use `tech.annexflow.lvm.android.library`; see [Android libraries](#android-libraries).
- **Test modules**, such as macrobenchmarks and baseline profile generators, use `tech.annexflow.lvm.android.test`; see [Test modules](#test-modules).
- **Kotlin Multiplatform libraries** get their Android target from `lvm { targets { android() } }`, which applies `com.android.kotlin.multiplatform.library`. This requires AGP 9.4 or newer.
  The namespace defaults to the module's `group` followed by its path (`com.example` and `:feature:user-profile` give `com.example.feature.user_profile`), so library modules should set `group` or `lvm { android { namespace = "..." } }`.
  Modules that configure the Android target with the native `kotlin { android { } }` DSL also add `alias(lvmLibs.plugins.android.kmp.library)` to their `plugins { }`; lvm then reuses the applied plugin, and values set natively win over `lvm { android { } }`.

### Applications

`tech.annexflow.lvm.android.application` applies AGP's `com.android.application` with its built-in Kotlin. It sets the SDK levels and the namespace from `lvm { android { } }`, the Java version from `lvm { jvm { } }`, and the application from `lvm { application { } }`: its ID and version, R8 and resource shrinking in the release build type with the keep rules, and core library desugaring with `desugar_jdk_libs`. It also passes `lvm { kotlin { } }` to the compiler, adds `kotlin-test-junit` to unit tests, sets the runner of instrumented tests and turns off their animations, and leaves `/META-INF/INDEX.LIST` and `/META-INF/DEPENDENCIES` out of the APK.

```kotlin
// app/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.android.application)
    alias(lvmLibs.plugins.blueprint.compose)
}

lvm {
    android { namespace = "com.example.app" }
    application {
        versionCode = 3
        versionName = "1.2.0"
    }
}
```

| Setting | Default | Effect |
|---|---|---|
| `android.targetSdk` | `37` | Target SDK level. `compileSdk`, `minSdk` and `namespace` work as for libraries |
| `android.testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` | Runner of instrumented tests. A module with instrumented tests declares `androidx.test:runner`, or Espresso, which depends on it |
| `application.applicationId` | unset | Application ID; AGP uses the namespace while it is unset |
| `application.versionCode` | unset | Version code, which CI can pass as `-Plvm.application.versionCode=42` |
| `application.versionName` | unset | Version name |
| `application.minify` | `true` | R8 and resource shrinking in the release build type |
| `application.proguardFiles` | `proguard-rules.pro` | Keep rules of the release build type after AGP's `proguard-android-optimize.txt`, relative to the module; files that don't exist are skipped |
| `application.desugaring` | `true` | Core library desugaring, with `desugar_jdk_libs` in `coreLibraryDesugaring` |

Values set with the native `android { }` DSL win. lvm fills in the SDK levels, the namespace, the application ID, the version and the keep rules only where the build script left them unset, and turns on minification and desugaring only where they are off; `minify = false` and `desugaring = false` keep them off. The Java version is set differently, because AGP doesn't let a plugin read it before the DSL is final: the plugin sets the build-wide `jvm.target` when it is applied, so `compileOptions` in the build script replace it, and a module's own `lvm { jvm { target } }` applies over both. Kotlin's JVM target follows the Java version.

Compose code needs the Compose compiler, which `tech.annexflow.lvm.compose` applies and configures as in Kotlin Multiplatform modules; see [Compose](#compose). [`samples/android-app`](samples/android-app) is an application with a Compose UI on `lvm-decompose-compose`.

### Android libraries

`tech.annexflow.lvm.android.library` sets up an Android library that doesn't use Kotlin Multiplatform, such as a library with product flavors, which `com.android.kotlin.multiplatform.library` doesn't have. It applies AGP's `com.android.library` with its built-in Kotlin and, like an application, sets the SDK levels, the namespace and the runner of instrumented tests from `lvm { android { } }` and the Java version from `lvm { jvm { } }`, passes `lvm { kotlin { } }` to the compiler, adds `kotlin-test-junit` to unit tests and turns off animations in instrumented tests. Strict explicit API mode follows `kotlin.explicitApi`, as in Kotlin Multiplatform libraries, and covers the main code of every variant. From `lvm { library { } }` it sets core library desugaring, the keep rules that ship in the AAR and a resource prefix, and it turns off the instrumented tests of a module that has no `androidTest` sources. Like an application, it leaves `/META-INF/INDEX.LIST` and `/META-INF/DEPENDENCIES` out of the APK of instrumented tests.

```kotlin
// core/ui/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.android.library)
    alias(lvmLibs.plugins.blueprint.compose)
}

group = "com.example"

android {
    flavorDimensions += "store"
    productFlavors {
        register("google") { dimension = "store" }
        register("rustore") { dimension = "store" }
    }
}
```

Flavors are declared with the native `android { }` DSL, and their dependency configurations, such as `rustoreImplementation`, work as usual. A flavor configuration that the build script itself creates has no Kotlin DSL accessor, so the script names it as a string: `"rustoreImplementation"(...)`. An application that doesn't have a library's dimension picks a flavor with `missingDimensionStrategy("store", "google")`; a library without flavors works in a flavored application without any setup.

| Setting | Default | Effect |
|---|---|---|
| `android.targetSdk` | `37` | Target SDK level of instrumented tests and lint; AGP has none for the library itself |
| `android.testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` | Runner of instrumented tests, as in applications |
| `library.desugaring` | `true` | Core library desugaring, with `desugar_jdk_libs` in `coreLibraryDesugaring`. An application that uses the library needs desugaring too |
| `library.consumerProguardFiles` | `consumer-rules.pro` | Keep rules that ship in the AAR, relative to the module; files that don't exist are skipped |
| `library.resourcePrefix` | `true` | Sets `resourcePrefix` to the module path: `:core:ui` gives `core_ui_`, `:feature:chat-history:ui` gives `feature_chat_history_ui_` |
| `library.resourcePrefixDroppedSegments` | empty | Segments that the resource prefix leaves out at the end of the module path; see below |
| `library.skipEmptyAndroidTest` | `true` | Turns off instrumented tests in a module without `androidTest/`, `androidTest@<name>/` or `src/androidTest*/` |

```kotlin
// settings.gradle.kts
lvm {
    library {
        consumerProguardFiles = emptyList()
        skipEmptyAndroidTest = false
    }
}
```

Values set with the native `android { }` DSL win, as in applications: lvm fills in the SDK levels, the namespace, the targets of tests and lint, the runner, the keep rules and the resource prefix only where the build script left them unset, and turns on desugaring only where it is off. The Java version works as in applications. AGP uses `resourcePrefix` only for lint's `ResourceName` check, which then expects every resource name to start with it; a module sets a prefix of its own with `android { resourcePrefix = "..." }`. With `library.resourcePrefixDroppedSegments`, modules share the prefix of their feature: segments in the list are left out at the end of the module path while more than one segment is left, where a segment is a run of letters, digits and `_`, compared without case. With `ui`, `sky` and `tweety`, `:feature:chat:ui-sky` and `:feature:chat:ui` give `feature_chat_`, `:core:ui:tweety` gives `core_`, and `:core:designsystem` keeps `core_designsystem_`. The rest of the path gives the prefix as without the list. Instrumented tests are turned off for the whole module or not at all: one `androidTest` directory keeps them for every variant.

[`samples/android-library`](samples/android-library) is a library with a `tier` dimension, which [`samples/android-app`](samples/android-app) uses.

### Test modules

`tech.annexflow.lvm.android.test` sets up a module of AGP's `com.android.test`, whose code tests an app from outside of it, such as a macrobenchmark or a baseline profile generator. It applies the plugin with its built-in Kotlin and, like an application, sets the SDK levels, the namespace and the runner of the tests from `lvm { android { } }` and the Java version from `lvm { jvm { } }`, passes `lvm { kotlin { } }` to the compiler, turns off animations and leaves `/META-INF/INDEX.LIST` and `/META-INF/DEPENDENCIES` out of the APK. The module names the app it tests natively, with `targetProjectPath`.

```kotlin
// benchmark/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.android.test)
}

android {
    targetProjectPath = ":app"
}
```

| Setting | Default | Effect |
|---|---|---|
| `android.targetSdk` | `37` | Target SDK level. `compileSdk`, `minSdk` and `namespace` work as for libraries |
| `android.testInstrumentationRunner` | `androidx.test.runner.AndroidJUnitRunner` | Runner of the module's tests |

Values set with the native `android { }` DSL win, and the Java version works as in applications. With the flat layout, the module's code is in `src/`, and in `src@<name>/` for a build type; see [Source layout](#source-layout). The module's code is all test code, so it gets no unit tests, no `kotlin-test` and no explicit API mode. Core library desugaring stays off: the module's APK only drives the app, which has desugaring of its own, and a module whose code calls Java APIs newer than its `minSdk` turns it on with `android { compileOptions { isCoreLibraryDesugaringEnabled = true } }` and adds `desugar_jdk_libs` to `coreLibraryDesugaring` itself.

## Kotlin/JVM

`tech.annexflow.lvm.jvm` sets up a Kotlin/JVM module, such as a KSP processor or a compiler plugin. It applies `org.jetbrains.kotlin.jvm`, sets the Java version from `lvm { jvm { } }`, passes `lvm { kotlin { } }` to the compiler, with strict explicit API mode for main code while `kotlin.explicitApi` is on, and adds `kotlin-test` to unit tests. [Kotlin and JVM](#kotlin-and-jvm) lists the values it uses.

```kotlin
// processor/build.gradle.kts
plugins {
    alias(lvmLibs.plugins.blueprint.jvm)
}
```

The flat layout, which `kotlin.flatLayout` turns on as in other modules, holds `main` in `src/` and `test` in `test/`, Kotlin and Java alike, test fixtures in `testFixtures/` and any other source set in `src@<name>/`, such as `src@integrationTest/`. Resources keep their conventional directories, `src/main/resources` and `src/test/resources`, as in the other plugins. With the flat layout, the module fails while it still has `src/<sourceSet>/kotlin` or `src/<sourceSet>/java`.

The Java version works as in Android modules: the plugin sets `java { sourceCompatibility; targetCompatibility }` to the build-wide `jvm.target` when it is applied, so a `java { }` block in the build script replaces it, and a module's own `lvm { jvm { target } }` applies over both. Kotlin's `jvmTarget` follows the Java version, and Java compiles with `options.release` of Kotlin's `jvmTarget`, so `kotlin { compilerOptions { jvmTarget = ... } }` in the build script sets both.

Unit tests run on JUnit 4, Gradle's default and the framework of Android unit tests. The plugin adds `kotlin-test`, and Kotlin picks the variant for the framework of the `test` task: `kotlin-test-junit` brings JUnit 4, which is all Gradle needs to run the tests. JUnit 5 and newer run on the JUnit Platform, which also needs `junit-platform-launcher` at the version of the module's JUnit engine, a version that only the module can choose. A module on the JUnit Platform calls `tasks.test { useJUnitPlatform() }`, which makes Kotlin pick `kotlin-test-junit5`, and declares the launcher. [`compiler/main-context-compiler`](compiler/main-context-compiler) is such a module.

## Compose

`tech.annexflow.lvm.compose` sets up a module for Compose: it applies the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) and configures the compiler's metrics, reports and stability configuration files. In a Kotlin Multiplatform module, it also applies Compose Multiplatform (`org.jetbrains.compose`). An Android application or library without Kotlin Multiplatform gets only the compiler, so a build with such modules alone needs only `org.jetbrains.kotlin.plugin.compose` on its build classpath. The plugin adds no dependencies; a module declares the Compose libraries it uses, which `lvm-versions` lists at the tested version.

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

In a module that also applies `tech.annexflow.lvm.quality`, the detekt Compose rules are on by default, and detekt accepts composable functions named like types. `lvm { quality { composeRules = false } }` turns the rules off. The quality plugin recognizes a Compose module by the Compose compiler plugin, so this also holds for an Android application that applies `org.jetbrains.kotlin.plugin.compose` itself.

## Publishing

`tech.annexflow.lvm.publish` publishes a module to Maven Central with [vanniktech's plugin](https://github.com/vanniktech/gradle-maven-publish-plugin): it sets the artifact ID, fills the POM from `lvm { publish { } }`, signs the publications and turns on Kotlin's ABI validation. It works for Kotlin Multiplatform and Kotlin/JVM modules and for the other kinds of modules that vanniktech's plugin knows, such as Gradle plugins. vanniktech's plugin must be on the build classpath, so the root project declares it next to the others, as in [Getting started](#2-declare-plugins-in-the-root-project).

The POM values are usually the same for every module, so they go into the settings:

```kotlin
// settings.gradle.kts
lvm {
    publish {
        artifactIdPrefix = "acme-"
        description = "Acme's libraries"
        url = "https://github.com/acme/libraries"
        license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        }
        developer {
            id = "acme"
            name = "Acme"
        }
    }
}
```

| Setting | Default | Effect |
|---|---|---|
| `artifactIdPrefix` | empty | Put before the project name to make the artifact ID, and the module's `archivesName`: `acme-` publishes `:core` as `acme-core`. Build-wide only |
| `name` | the artifact ID | Name in the POM |
| `description`, `url` | none | Description and web page in the POM. Required |
| `license { name; url }` | none | License in the POM. Required |
| `developer { id; name; url }` | none | Developer in the POM. `id` and `name` are required |
| `scm { url; connection; developerConnection }` | `url`, and `scm:git:<url>.git` for both connections | Source repository in the POM |
| `signing` | `true` | Signs the publications. Versions that don't end in `-SNAPSHOT` need a key |
| `abi { enabled }` | `true` | Kotlin's ABI validation. Build-wide only |
| `abi { excludedNames; excludedAnnotations }` | empty | Classes and members by name, and annotations, whose declarations stay out of the ABI dump |

A POM without a required value fails to generate and names the `lvm.publish.*` keys to set. `publishToMavenCentral` uploads a deployment, and `publishAndReleaseToMavenCentral` also releases it. Both read vanniktech's Gradle properties for the Central Portal token (`mavenCentralUsername`, `mavenCentralPassword`) and the signing key (`signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`), for example as `ORG_GRADLE_PROJECT_*` environment variables in CI. To try a release version in Maven local without a key, turn signing off with `-Plvm.publish.signing=false`.

With ABI validation on, `check` runs `checkKotlinAbi`, which compares the module's public API with the dump in its `api/` folder. `updateKotlinAbi` writes the dump; commit it, and update it when an API change is intended.

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

The plugin works the same in Android modules without Kotlin Multiplatform, such as those of `tech.annexflow.lvm.android.library`, and in Kotlin/JVM modules, such as those of `tech.annexflow.lvm.jvm`.

The plugin adds `lvm-main-context`, which holds the annotation, and runs the compiler plugin `lvm-main-context-compiler` of the same version. The compiler plugin moves each call, together with its receiver and arguments, into `withContext(Dispatchers.Main.immediate)`. A call made on the main thread runs at once.

- Calls move from suspend functions, suspend lambdas and lambdas passed to inline functions there, unless the parameter is `crossinline` or `noinline`. Calls from other code, calls through function references and calls in default parameter values run where they are made.
- Receivers and arguments are computed on the main thread too, so compute expensive ones before the call, as `open` does.
- Overrides of an annotated function inherit the annotation, also from other modules.
- A call whose receiver or arguments `return`, `break` or `continue` out of it, as in `push(screen ?: return)`, is a compile error. Compute the value in a variable first.

`Dispatchers.Main` comes from `kotlinx-coroutines-android` on Android, which `lvm-main-context` depends on, and from `kotlinx-coroutines-core` on Apple platforms. On JVM it needs `kotlinx-coroutines-swing` or `kotlinx-coroutines-javafx`; Compose Desktop adds the first. Without a main dispatcher, a moved call fails with an `IllegalStateException`. Tests set one with `Dispatchers.setMain` from `kotlinx-coroutines-test`. With `testCompilations = false`, calls in tests run where they are made, so a test can call a `@MainContext` function from `runTest` without a main dispatcher. Calls in main code still move, so a test of code that makes such calls, such as a component, still needs `Dispatchers.setMain`.

| Setting | Default | Effect |
|---|---|---|
| `platforms` | `android`, `ios`, `jvm` | Platforms whose compilations run the compiler plugin. An empty list turns it off |
| `testCompilations` | `true` | Runs the compiler plugin on test compilations too. `false` keeps it to main code: unit tests, instrumented tests, test fixtures and other source sets, such as `src@integrationTest`, compile without it |
| `dependency` | `true` | Adds `lvm-main-context` to `commonMain`, or to `implementation` in a module without Kotlin Multiplatform |

```kotlin
// feature/build.gradle.kts
lvm {
    mainContext {
        platforms.set(listOf("android", "ios"))
        testCompilations = false
    }
}
```

The compiler plugin works only with the Kotlin version it was built for, or a version that differs from it only in the last digit; see [Supported versions](#supported-versions).

## Supported versions

LVM requires Gradle 9.6, AGP 9.4 and Kotlin 2.4 or newer. Your project chooses its Gradle, AGP, Kotlin, Compose and detekt versions. LVM is tested with the versions listed in `lvm-versions` and with the Gradle version of its own wrapper.

The minimums are strict on purpose and will stay that way. They follow recent Gradle, AGP and Kotlin releases and move up whenever LVM moves to newer tools; older toolchains are not kept alive. If your project stays on older versions, stay on the LVM release that supports them.

Blueprint checks the Gradle, AGP and Kotlin versions: a version below the minimum fails the build with a clear error, and a version newer than tested produces a warning. For Gradle, that warning comes only from `tech.annexflow.lvm.settings`, once per build; without the settings plugin, the project plugins still enforce the minimum. Compose and detekt versions are not checked.

One exception to choosing your own Kotlin version is `tech.annexflow.lvm.main-context`: its compiler plugin depends on compiler APIs that can change in any Kotlin release, so projects that apply it must use the Kotlin version from `lvm-versions` or a version that differs from it only in the last digit of the patch number. A compiler plugin built with Kotlin 2.4.20 also runs on the bugfix releases 2.4.21 and 2.4.22, with a warning. Any other version, such as 2.4.30 or 2.5.0, fails the build.

Blueprint's API for your own convention plugins, described in [Build logic](docs/build-logic.md), stays binary-compatible within a minor version: build logic compiled against 0.2.0 works with every 0.2.x release, and 0.3.0 may change it.

## Documentation

- [Releasing](RELEASING.md): how releases are published
- [iOS](docs/ios.md): how an iOS app runs a component tree of `lvm-decompose`
- [Build logic](docs/build-logic.md): your own convention plugins on top of LVM's
- [Continuous integration](docs/ci.md): the CI jobs and the self-hosted macOS runner
- [The lvm command](docs/tui.md): a terminal app that shows, checks, runs and upgrades a build that uses LVM
- [The Claude Code plugin](docs/claude-code.md): skills that let Claude read, check, upgrade and create builds that use LVM

## License

[Apache License 2.0](LICENSE)
