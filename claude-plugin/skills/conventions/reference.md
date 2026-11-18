# LVM's plugins in more detail

Each part summarizes a section of LVM's README. The links go to `main`; for the build's own release, replace `main` with `v<version>` in the URL.

## Android

* An application applies `tech.annexflow.lvm.android.application`. It sets the SDK levels and the namespace from `lvm { android { } }`, the Java version from `lvm { jvm { } }`, and from `lvm { application { } }` the application ID, `versionCode`, `versionName`, R8 and resource shrinking in the release build type (`minify`), the keep rules (`proguardFiles`) and core library desugaring (`desugaring`). CI can pass `-Plvm.application.versionCode=42`.
* An Android library without Kotlin Multiplatform, such as one with product flavors, applies `tech.annexflow.lvm.android.library`. `lvm { library { } }` sets `desugaring`, `consumerProguardFiles`, `resourcePrefix` (the module path, as in `core_ui_`) and `skipEmptyAndroidTest`. Flavors are declared with the native `android { }` DSL.
* A Kotlin Multiplatform library gets its Android target from `lvm { targets { android() } }`. Its namespace defaults to the module's `group` followed by its path, so a library sets `group` or `lvm { android { namespace = "..." } }`. A module that also configures `kotlin { android { } }` natively adds `alias(lvmLibs.plugins.android.kmp.library)` to its plugins.
* Values set with the native `android { }` DSL win: lvm fills in only what the build script leaves unset.

See [Android](https://github.com/Lavmee/LVM-KMP/blob/main/README.md#android).

## Compose

`tech.annexflow.lvm.compose` applies the Compose compiler, and Compose Multiplatform in a Kotlin Multiplatform module. It adds no dependencies: a module declares the Compose libraries it uses, such as `lvmLibs.compose.foundation`. `lvm.compose.metrics` and `lvm.compose.reports` write the compiler's metrics and reports into `build/compose-metrics` and `build/compose-reports`, and `stabilityConfigurationFiles` are relative to the root project. With `tech.annexflow.lvm.quality`, detekt's Compose rules are on, and `lvm { quality { composeRules = false } }` turns them off. A build whose Compose code is only in Android modules without Kotlin Multiplatform doesn't declare `compose.multiplatform` in the root project.

See [Compose](https://github.com/Lavmee/LVM-KMP/blob/main/README.md#compose).

## Publishing

`tech.annexflow.lvm.publish` publishes a module to Maven Central with vanniktech's plugin, which the root project declares. The artifact ID is `artifactIdPrefix` followed by the project name. The POM comes from `lvm { publish { } }`, usually in `settings.gradle.kts` or as `lvm.publish.*` properties: `description`, `url`, `license { name; url }` and `developer { id; name }` are required. Publications are signed, and a version without `-SNAPSHOT` needs a key; `-Plvm.publish.signing=false` turns signing off to try a release in Maven local. The Central Portal token and the signing key are vanniktech's Gradle properties (`mavenCentralUsername`, `mavenCentralPassword`, `signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`), never written into the build. With ABI validation on, `check` runs `checkKotlinAbi` against the dump in `api/`; after an intended API change, `updateKotlinAbi` writes the dump, which is committed.

See [Publishing](https://github.com/Lavmee/LVM-KMP/blob/main/README.md#publishing).

## Main context

`tech.annexflow.lvm.main-context` runs every call to a function annotated with `@MainContext` from a coroutine on `Dispatchers.Main.immediate`. It adds `lvm-main-context` and the compiler plugin `lvm-main-context-compiler` of the same version. Receivers and arguments are computed on the main thread too, so compute expensive ones before the call. A call whose argument returns, breaks or continues out of it, as in `push(screen ?: return)`, is a compile error: compute the value in a variable first. On JVM, the app needs `kotlinx-coroutines-swing` or `kotlinx-coroutines-javafx` for a main dispatcher. `lvm { mainContext { platforms } }` picks the platforms. The compiler plugin works only with the Kotlin version in `lvm-versions` or one that differs from it only in the last digit.

See [Main context](https://github.com/Lavmee/LVM-KMP/blob/main/README.md#main-context).

## Supported versions

LVM requires minimum versions of Gradle, AGP and Kotlin, and `toolchain` in `lvm report` lists them with the build's own versions. A version below the minimum fails the build, and a version newer than the tested one produces a warning. The minimums move up with LVM releases, so a build that stays on older tools stays on an older LVM release. Compose and detekt versions are not checked.

See [Supported versions](https://github.com/Lavmee/LVM-KMP/blob/main/README.md#supported-versions).
