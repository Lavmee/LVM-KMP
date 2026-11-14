package tech.annexflow.lvm.blueprint.android

import java.io.File
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.agpVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

/**
 * Prints the DSL as lvm's finalizeDsl left it, prefixed with `lvm-dsl`: finalizeDsl callbacks run in registration
 * order, and lvm registers its own when the plugin is applied. AGP throws when finalizeDsl reads the Java version, so
 * tests read it from class files instead.
 */
const val PRINT_DSL: String = """
androidComponents.finalizeDsl { android ->
    val config = android.defaultConfig
    val release = android.buildTypes.getByName("release")
    println(
        "lvm-dsl namespace=" + android.namespace + " applicationId=" + config.applicationId +
            " compileSdk=" + android.compileSdk + " minSdk=" + config.minSdk + " targetSdk=" + config.targetSdk +
            " versionCode=" + config.versionCode + " versionName=" + config.versionName,
    )
    println(
        "lvm-dsl desugaring=" + android.compileOptions.isCoreLibraryDesugaringEnabled +
            " minify=" + release.isMinifyEnabled + " shrink=" + release.isShrinkResources +
            " proguard=" + release.proguardFiles.map { it.name } +
            " animationsDisabled=" + android.testOptions.animationsDisabled +
            " excludes=" + android.packaging.resources.excludes.filter { it in setOf("/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES") }.sorted(),
    )
}
"""

/** A build with one application module, `:app`, in group `com.example`, whose build script ends with [body]. */
fun applicationFixture(dir: File, body: String, settingsLvm: String = "catalog = false"): GradleFixture =
    GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":app\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "$agpVersion" apply false
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.android.application") apply false
            }
            """,
        )
        file(
            "app/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.android.application") }
            group = "com.example"
            """ + body,
        )
        file("app/src/main/AndroidManifest.xml", "<manifest />")
        file("app/src/main/kotlin/Hello.kt", "class Hello")
        writeLocalProperties()
    }

/** The class file of `Hello`, which AGP's built-in Kotlin compiles for the debug variant. */
fun File.debugClass(name: String): File =
    resolve("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/$name.class")

fun classFileMajorVersion(classFile: File): Int {
    val bytes = classFile.readBytes()
    return ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
}
