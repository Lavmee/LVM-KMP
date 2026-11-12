package tech.annexflow.lvm.blueprint.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader
import tech.annexflow.lvm.blueprint.testing.LvmTestVersions

class LvmComposePluginTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `a module compiles composables and writes no metrics or reports by default`() {
        val fixture = fixture()

        fixture.build(":lib:compileKotlinJvm")

        assertFalse(dir.resolve("lib/build/compose-metrics").exists())
        assertFalse(dir.resolve("lib/build/compose-reports").exists())
    }

    @Test
    fun `metrics and reports follow the Gradle property, settings and the module`() {
        val fixture = fixture(settingsLvm = "catalog = false\ncompose { reports = true }")
        fixture.file(
            "lib/build.gradle.kts",
            module("lvm { compose { reportsDirectory = layout.buildDirectory.dir(\"stability\") } }"),
        )

        fixture.build(":lib:compileKotlinJvm", "-Plvm.compose.metrics=true")

        assertTrue(dir.resolve("lib/build/compose-metrics").hasFileEndingWith("-module.json"))
        assertTrue(dir.resolve("lib/build/stability").hasFileEndingWith("-classes.txt"))
        assertFalse(dir.resolve("lib/build/compose-reports").exists())
    }

    /** List is not stable to the Compose compiler; Instant is a class it doesn't know. */
    @Test
    fun `stability files from settings and the module both reach the compiler`() {
        val fixture = fixture(
            settingsLvm = "catalog = false\ncompose { reports = true; stabilityConfigurationFiles.add(\"build-wide.conf\") }",
        )
        fixture.file("build-wide.conf", "kotlin.collections.List")
        fixture.file("module.conf", "kotlin.time.Instant")
        fixture.file("lib/build.gradle.kts", module("lvm { compose { stabilityConfigurationFiles.add(\"module.conf\") } }"))

        fixture.build(":lib:compileKotlinJvm")

        val classes = dir.resolve("lib/build/compose-reports").walk().single { it.name.endsWith("-classes.txt") }
        assertContains(classes.readText(), "stable class Holder")
        assertFalse(classes.readText().contains("unstable class Holder"))
    }

    private fun fixture(settingsLvm: String = "catalog = false"): GradleFixture = GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "$kotlinVersion" apply false
                id("org.jetbrains.compose") version "${LvmTestVersions.COMPOSE_MULTIPLATFORM}" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
                id("tech.annexflow.lvm.compose") apply false
            }
            """,
        )
        file("lib/build.gradle.kts", module())
        file(
            "lib/src/commonMain/kotlin/Holder.kt",
            """
            import kotlin.time.Instant

            class Holder(val names: List<String>, val createdAt: Instant)
            """,
        )
        file(
            "lib/src/commonMain/kotlin/Greeting.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Greeting(holder: Holder) {
                holder.names.size
            }
            """,
        )
    }

    /** A JVM module with the compose plugin, the Compose runtime and [extra] lines. */
    private fun module(extra: String = ""): String = """
        plugins {
            id("tech.annexflow.lvm.compose")
            id("tech.annexflow.lvm.multiplatform")
        }
        lvm { targets { jvm() } }
        kotlin {
            sourceSets.commonMain.dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:${LvmTestVersions.COMPOSE_MULTIPLATFORM}")
            }
        }
        $extra
    """

    // Metrics go into a directory per compilation, such as jvm/main; reports don't.
    private fun File.hasFileEndingWith(suffix: String): Boolean = walk().any { it.name.endsWith(suffix) }
}
