package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.agpVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

const val JVM_MODULE: String = """
plugins { id("tech.annexflow.lvm.multiplatform") }
lvm { targets { jvm() } }
"""

/** A build with one KMP module at [modulePath]. AGP is on the classpath only when [withAgp] is set. */
fun kmpFixture(
    dir: File,
    module: String,
    settingsLvm: String = "catalog = false",
    modulePath: String = ":lib",
    withAgp: Boolean = false,
): GradleFixture = GradleFixture(dir).apply {
    file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\"$modulePath\")")
    val agp = if (withAgp) "id(\"com.android.kotlin.multiplatform.library\") version \"$agpVersion\" apply false" else ""
    file(
        "build.gradle.kts",
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
            $agp
            id("tech.annexflow.lvm.multiplatform") apply false
        }
        """,
    )
    val moduleDir = modulePath.removePrefix(":").replace(':', '/')
    file("$moduleDir/build.gradle.kts", module)
    file("$moduleDir/src/commonMain/kotlin/Hello.kt", "class Hello")
}

fun classFileMajorVersion(classFile: File): Int {
    val bytes = classFile.readBytes()
    return ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
}
