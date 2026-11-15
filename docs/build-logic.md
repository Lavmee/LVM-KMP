# Build logic

Your own convention plugins can build on LVM's plugins. They can read the build-wide values that LVM's plugins start from, such as the SDK levels, the JVM target and the Kotlin options, and configure the `lvm { }` sections of a module. This guide covers the setup, the build-wide values, the sections and what blueprint supports. Gradle's guide to [sharing build logic between subprojects](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html) explains convention plugins in general.

## Setup

Build logic, such as an included `build-logic` build, compiles against blueprint with `compileOnly`. At runtime, your plugins use the classes of the LVM plugins that your build loads, so declare LVM's plugins in the root project, as [Getting started](../README.md#2-declare-plugins-in-the-root-project) shows.

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("tech.annexflow.lvm:lvm-blueprint-quality:<version>")
}
```

Every plugin artifact brings `lvm-blueprint-common`, which holds the build-wide values and the sections that several plugins share. Depend on the artifact of each plugin whose section you configure, or on `lvm-blueprint-common` alone if you only read values. Use the version of the LVM plugins in your build.

## Build-wide values

`project.lvmValues` holds the build-wide values of the `android`, `jvm` and `kotlin` sections. Each value comes from the `lvm.<section>.<name>` Gradle property, then from `lvm { }` in `settings.gradle.kts`, then from LVM's default, as [Configuration](../README.md#configuration) describes. A module's own `lvm { }` block doesn't change them, so every module reads the same values.

```kotlin
// A Kotlin/JVM module with the build's JVM target and Kotlin options.
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        val values = project.lvmValues
        project.extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(values.jvm.target.map { JvmTarget.fromTarget(it.toString()) })
                allWarningsAsErrors.set(values.kotlin.warningsAsErrors)
                optIn.addAll(values.kotlin.optIns)
                freeCompilerArgs.addAll(values.kotlin.freeCompilerArgs)
            }
        }
        project.tasks.withType<JavaCompile>().configureEach {
            options.release.set(values.jvm.target)
        }
    }
}
```

The values are lazy `Provider`s. Pass them to properties that take a provider, and call `get()` where an API needs a plain value. Reading a value is safe at any time during configuration, also while your plugin is being applied. A value that can't be parsed, such as `lvm.android.minSdk=abc`, fails when it is read.

## Sections of a module

LVM's plugins add their sections to the `lvm { }` block of the modules they are applied to, for example `quality` from `tech.annexflow.lvm.quality`. Build scripts reach them through the accessors that Gradle generates. Plugins use the accessors that blueprint declares next to each section, such as `lvm.quality`.

```kotlin
// Modules with Compose get the build's detekt config for Compose code.
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("tech.annexflow.lvm.quality") {
            project.lvm.quality {
                config.add("config/detekt/compose.yml")
            }
        }
    }
}
```

Each section has an accessor of the same name in the artifact of the plugin that adds it, such as `lvm.targets` in `lvm-blueprint-multiplatform`. The accessors of the sections that several plugins share, `lvm.android`, `lvm.jvm` and `lvm.kotlin`, are in `lvm-blueprint-common`.

A section exists once a plugin that adds it is applied. Until then, its accessor fails with an error that names that plugin, so configure a section inside `pluginManager.withPlugin(...)` when your plugin may be applied first. A section holds the module's own values, which the build script sets over the build-wide values. The build script runs after your plugin is applied, so read a section's values lazily.

## What blueprint supports

Blueprint supports what build logic needs: `lvmValues` and its classes, `Project.lvm`, the sections and their accessors, the settings extension and the plugin classes. Every blueprint artifact checks them against a committed ABI dump, and [Supported versions](../README.md#supported-versions) states how they may change.

Declarations marked `@InternalBlueprintApi`, such as `LvmValues`, `LvmKeys` and `LvmDefaults`, are shared among LVM's own plugins and may change in any release. Code that uses them doesn't compile without `@OptIn(InternalBlueprintApi::class)`. The generated `LvmBuildInfo` is not supported either.
