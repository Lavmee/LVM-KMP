package tech.annexflow.lvm.blueprint.android

import java.io.File
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.agpVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

/** Prints the DSL of a test module as lvm's finalizeDsl left it, prefixed with `lvm-dsl`, as [PRINT_DSL] does. */
const val PRINT_TEST_MODULE_DSL: String = """
androidComponents.finalizeDsl { android ->
    val config = android.defaultConfig
    println(
        "lvm-dsl namespace=" + android.namespace + " compileSdk=" + android.compileSdk + " minSdk=" + config.minSdk +
            " targetSdk=" + config.targetSdk + " runner=" + config.testInstrumentationRunner,
    )
    println(
        "lvm-dsl desugaring=" + android.compileOptions.isCoreLibraryDesugaringEnabled +
            " animationsDisabled=" + android.testOptions.animationsDisabled,
    )
}
"""

const val TEST_MODULE_HEADER: String = """
plugins { id("tech.annexflow.lvm.android.test") }
group = "com.example"
android { targetProjectPath = ":app" }
"""

/**
 * A build with an application, `:app`, and a test module of it, `:benchmark`, in group `com.example`, whose build script
 * ends with [body].
 */
fun testModuleFixture(dir: File, body: String, settingsLvm: String = "catalog = false"): GradleFixture =
    GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":app\", \":benchmark\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "$agpVersion" apply false
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.android.application") apply false
                id("tech.annexflow.lvm.android.test") apply false
            }
            """,
        )
        file("app/build.gradle.kts", "plugins { id(\"tech.annexflow.lvm.android.application\") }\ngroup = \"com.example\"")
        file("app/src/main/AndroidManifest.xml", "<manifest />")
        file("benchmark/build.gradle.kts", TEST_MODULE_HEADER + body)
        file("benchmark/src/main/AndroidManifest.xml", "<manifest />")
        file("benchmark/src/main/kotlin/Hello.kt", "class Hello")
        writeLocalProperties()
    }

/** The class file of [name], which AGP's built-in Kotlin compiles for the test module's debug variant. */
fun File.testModuleClass(name: String): File =
    resolve("benchmark/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/$name.class")
