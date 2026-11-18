package tech.annexflow.lvm.blueprint.android

import java.io.File
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.agpVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

/**
 * Prints the DSL of a library as lvm's finalizeDsl left it, prefixed with `lvm-dsl`, and whether each variant has
 * instrumented tests, prefixed with `lvm-variant`. Both callbacks run after lvm's, which it registers when the plugin
 * is applied.
 */
const val PRINT_LIBRARY_DSL: String = """
androidComponents.finalizeDsl { android ->
    val config = android.defaultConfig
    println(
        "lvm-dsl namespace=" + android.namespace + " compileSdk=" + android.compileSdk + " minSdk=" + config.minSdk +
            " testTargetSdk=" + android.testOptions.targetSdk + " lintTargetSdk=" + android.lint.targetSdk +
            " runner=" + config.testInstrumentationRunner + " resourcePrefix=" + android.resourcePrefix,
    )
    println(
        "lvm-dsl desugaring=" + android.compileOptions.isCoreLibraryDesugaringEnabled +
            " consumerProguardFiles=" + config.consumerProguardFiles.map { it.name } +
            " animationsDisabled=" + android.testOptions.animationsDisabled +
            " excludes=" + android.packaging.resources.excludes.filter { it in setOf("/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES") }.sorted(),
    )
}
androidComponents.onVariants { variant ->
    println("lvm-variant " + variant.name + " androidTest=" + (variant.androidTest != null))
}
"""

/** A build with one Android library, `:core:ui`, in group `com.example`, whose build script ends with [body]. */
fun libraryFixture(dir: File, body: String, settingsLvm: String = "catalog = false"): GradleFixture =
    GradleFixture(dir).apply {
        file("settings.gradle.kts", librarySettings(settingsLvm))
        file(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.library") version "$agpVersion" apply false
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.android.library") apply false
            }
            """,
        )
        file(
            "core/ui/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.android.library") }
            group = "com.example"
            """ + body,
        )
        file("core/ui/src/main/AndroidManifest.xml", "<manifest />")
        file("core/ui/src/main/kotlin/Hello.kt", "class Hello")
        writeLocalProperties()
    }

fun librarySettings(settingsLvm: String): String =
    settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":core:ui\")"

/** The class file of [name], which AGP's built-in Kotlin compiles for the library's [variant]. */
fun File.libraryClass(name: String, variant: String = "debug"): File {
    val task = "compile" + variant.replaceFirstChar(Char::uppercaseChar) + "Kotlin"
    return resolve("core/ui/build/intermediates/built_in_kotlinc/$variant/$task/classes/$name.class")
}
