package tech.annexflow.lvm.blueprint.common

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

fun Project.lvmAndroid(): AndroidSection = lvm.section<AndroidSection>("android") { section ->
    val values = LvmValues(this)
    section.compileSdk.convention(values.int(LvmKeys.ANDROID_COMPILE_SDK, LvmDefaults.ANDROID_COMPILE_SDK))
    section.minSdk.convention(values.int(LvmKeys.ANDROID_MIN_SDK, LvmDefaults.ANDROID_MIN_SDK))
    section.namespace.convention(provider { defaultNamespace(group.toString(), path) })
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

/** `com.example` + `:feature:user-profile` → `com.example.feature.user_profile`. */
fun defaultNamespace(group: String, projectPath: String): String {
    if (group.isBlank()) {
        throw InvalidUserDataException(
            "Cannot derive the Android namespace of $projectPath: set the project group or lvm { android { namespace = \"…\" } }.",
        )
    }
    val segments = projectPath.split(':').filter(String::isNotEmpty).map { it.replace('-', '_') }
    return (listOf(group) + segments).joinToString(".")
}
