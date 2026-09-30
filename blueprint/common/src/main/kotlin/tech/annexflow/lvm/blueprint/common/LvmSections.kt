package tech.annexflow.lvm.blueprint.common

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

@Suppress("UnstableApiUsage") // Project.isolated: reads only the root name, safe under Isolated Projects.
fun Project.lvmAndroid(): AndroidSection = lvm.section<AndroidSection>("android") { section ->
    val values = LvmValues(this)
    section.compileSdk.convention(values.int(LvmKeys.ANDROID_COMPILE_SDK, LvmDefaults.ANDROID_COMPILE_SDK))
    section.minSdk.convention(values.int(LvmKeys.ANDROID_MIN_SDK, LvmDefaults.ANDROID_MIN_SDK))
    section.namespace.convention(
        provider {
            // A group Gradle derived from the project path is not one the module chose, so it does not name the namespace.
            val chosenGroup = group.toString().takeUnless { it == gradleImplicitGroup(isolated.rootProject.name, path) }
            defaultNamespace(chosenGroup.orEmpty(), path)
        },
    )
}

fun Project.lvmJvm(): JvmSection = lvm.section<JvmSection>("jvm") { section ->
    section.target.convention(LvmValues(this).int(LvmKeys.JVM_TARGET, LvmDefaults.JVM_TARGET))
}

fun Project.lvmKotlin(): KotlinSection = lvm.section<KotlinSection>("kotlin") { section ->
    val values = LvmValues(this)
    section.warningsAsErrors.convention(values.boolean(LvmKeys.KOTLIN_WARNINGS_AS_ERRORS, LvmDefaults.KOTLIN_WARNINGS_AS_ERRORS))
    section.explicitApi.convention(values.boolean(LvmKeys.KOTLIN_EXPLICIT_API, LvmDefaults.KOTLIN_EXPLICIT_API))
    section.testDependencies.convention(values.boolean(LvmKeys.KOTLIN_TEST_DEPENDENCIES, LvmDefaults.KOTLIN_TEST_DEPENDENCIES))
}

/** `com.example` + `:feature:user-profile` -> `com.example.feature.user_profile`. */
fun defaultNamespace(group: String, projectPath: String): String {
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
fun gradleImplicitGroup(rootProjectName: String, projectPath: String): String {
    val segments = projectPath.split(':').filter(String::isNotEmpty)
    if (segments.isEmpty()) return ""
    return (listOf(rootProjectName) + segments.dropLast(1)).joinToString(".")
}
