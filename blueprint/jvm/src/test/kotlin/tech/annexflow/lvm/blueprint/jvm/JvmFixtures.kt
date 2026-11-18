package tech.annexflow.lvm.blueprint.jvm

import java.io.File
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

const val JVM_MODULE_HEADER: String = "plugins { id(\"tech.annexflow.lvm.jvm\") }\n"

/** A build with one Kotlin/JVM module, `:lib`, whose build script ends with [body]. */
fun jvmFixture(dir: File, body: String = "", settingsLvm: String = "catalog = false"): GradleFixture =
    GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.jvm") apply false
            }
            """,
        )
        file("lib/build.gradle.kts", JVM_MODULE_HEADER + body)
        file("lib/src/main/kotlin/Hello.kt", "class Hello")
    }

/** The class file of [name] in the main classes that [compiler], `kotlin` or `java`, wrote. */
fun File.mainClass(name: String, compiler: String = "kotlin"): File = resolve("lib/build/classes/$compiler/main/$name.class")

fun classFileMajorVersion(classFile: File): Int {
    val bytes = classFile.readBytes()
    return ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
}
