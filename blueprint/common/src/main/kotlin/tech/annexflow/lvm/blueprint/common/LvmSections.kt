package tech.annexflow.lvm.blueprint.common

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

@InternalBlueprintApi
fun Project.lvmAndroid(): AndroidSection = lvm.section<AndroidSection>("android") { section ->
    val values = lvmValues.android
    section.compileSdk.convention(values.compileSdk)
    section.minSdk.convention(values.minSdk)
    section.targetSdk.convention(values.targetSdk)
    section.testInstrumentationRunner.convention(values.testInstrumentationRunner)
    section.namespace.convention(conventionalNamespace())
}

/**
 * Adds the values of `lvm { android { } }` to the module's `lvmReport`. Plugins call it where they set up Android, so a
 * module without Android has none in its report.
 */
@InternalBlueprintApi
fun Project.reportAndroidValues(section: AndroidSection) {
    val layers = LvmValues(this)
    val buildWide = lvmValues.android
    layers.report(LvmKeys.ANDROID_COMPILE_SDK, section.compileSdk, buildWide.compileSdk)
    layers.report(LvmKeys.ANDROID_MIN_SDK, section.minSdk, buildWide.minSdk)
    layers.report(LvmKeys.ANDROID_TARGET_SDK, section.targetSdk, buildWide.targetSdk)
    layers.report(LvmKeys.ANDROID_TEST_INSTRUMENTATION_RUNNER, section.testInstrumentationRunner, buildWide.testInstrumentationRunner)
    // Without a group, the default namespace fails when it is read; the report then has none.
    reportModuleValue("android.namespace", orNullOnFailure(section.namespace), orNullOnFailure(conventionalNamespace()))
}

@InternalBlueprintApi
fun Project.lvmJvm(): JvmSection = lvm.section<JvmSection>("jvm") { section ->
    val buildWide = lvmValues.jvm
    section.target.convention(buildWide.target)
    LvmValues(this).report(LvmKeys.JVM_TARGET, section.target, buildWide.target)
}

@InternalBlueprintApi
fun Project.lvmKotlin(): KotlinSection = lvm.section<KotlinSection>("kotlin") { section ->
    val values = lvmValues.kotlin
    section.warningsAsErrors.convention(values.warningsAsErrors)
    section.explicitApi.convention(values.explicitApi)
    section.testDependencies.convention(values.testDependencies)
    section.flatLayout.convention(values.flatLayout)
    section.checkSourceLayout.convention(values.checkSourceLayout)
    section.jvmDefault.convention(values.jvmDefault)

    val layers = LvmValues(this)
    layers.report(LvmKeys.KOTLIN_WARNINGS_AS_ERRORS, section.warningsAsErrors, values.warningsAsErrors)
    // A module appends to these lists, so its value is the build-wide list followed by its own entries.
    layers.report(LvmKeys.KOTLIN_OPT_INS, values.optIns.zip(section.optIns) { buildWide, module -> buildWide + module }, values.optIns)
    layers.report(
        LvmKeys.KOTLIN_FREE_COMPILER_ARGS,
        values.freeCompilerArgs.zip(section.freeCompilerArgs) { buildWide, module -> buildWide + module },
        values.freeCompilerArgs,
    )
    layers.report(LvmKeys.KOTLIN_EXPLICIT_API, section.explicitApi, values.explicitApi)
    layers.report(LvmKeys.KOTLIN_TEST_DEPENDENCIES, section.testDependencies, values.testDependencies)
    layers.report(LvmKeys.KOTLIN_FLAT_LAYOUT, section.flatLayout, values.flatLayout)
    layers.report(LvmKeys.KOTLIN_CHECK_SOURCE_LAYOUT, section.checkSourceLayout, values.checkSourceLayout)
    layers.report(LvmKeys.KOTLIN_JVM_DEFAULT, section.jvmDefault, values.jvmDefault)
}

/** The namespace of a module that sets none: its group followed by its path. Reading it fails without a group. */
@Suppress("UnstableApiUsage") // Project.isolated: reads only the root name, safe under Isolated Projects.
private fun Project.conventionalNamespace(): Provider<String> = provider {
    // A group Gradle derived from the project path is not one the module chose, so it does not name the namespace.
    val chosenGroup = group.toString().takeUnless { it == gradleImplicitGroup(isolated.rootProject.name, path) }
    defaultNamespace(chosenGroup.orEmpty(), path)
}

/** The value of [provider], or none where reading it fails. */
private fun <T : Any> Project.orNullOnFailure(provider: Provider<T>): Provider<T> =
    provider { runCatching { provider.orNull }.getOrNull() }

/** `com.example` + `:feature:user-profile` -> `com.example.feature.user_profile`. */
internal fun defaultNamespace(group: String, projectPath: String): String {
    if (group.isBlank()) {
        throw InvalidUserDataException(
            "Cannot derive the Android namespace of $projectPath: set group = \"...\" in the module, " +
                "or lvm { android { namespace = \"...\" } }.",
        )
    }
    val segments = projectPath.split(':').filter(String::isNotEmpty).map { it.replace('-', '_') }
    return (listOf(group) + segments).joinToString(".")
}

/**
 * The group Gradle assigns to a project that sets none: empty for the root project, the root project name for its
 * children, and the root project name followed by the parent path below that (`my-app` + `:feature:user-profile` -> `my-app.feature`).
 */
internal fun gradleImplicitGroup(rootProjectName: String, projectPath: String): String {
    val segments = projectPath.split(':').filter(String::isNotEmpty)
    if (segments.isEmpty()) return ""
    return (listOf(rootProjectName) + segments.dropLast(1)).joinToString(".")
}
