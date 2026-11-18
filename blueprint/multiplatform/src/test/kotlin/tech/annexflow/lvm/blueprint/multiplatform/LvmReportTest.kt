package tech.annexflow.lvm.blueprint.multiplatform

import groovy.json.JsonSlurper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

/**
 * `lvm-report.json` in the test resources is the report of `:lib` below, and the example that lvm-tui's tests read.
 * Fields that change with the versions of LVM, Gradle and Kotlin are compared by their content only.
 */
class LvmReportTest {

    @TempDir
    lateinit var dir: File

    private fun fixture(): GradleFixture = GradleFixture(dir).apply {
        file(
            "settings.gradle.kts",
            settingsHeader(
                """
                catalog = false
                jvm { target = 17 }
                kotlin { optIns.add("kotlin.ExperimentalStdlibApi") }
                """.trimIndent(),
            ) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\", \":core\")",
        )
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
            }
            """,
        )
        file("core/build.gradle.kts", JVM_MODULE)
        file(
            "lib/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.multiplatform") }
            lvm {
                targets {
                    jvm()
                    ios { framework { baseName = "Lib"; isStatic = true } }
                }
                kotlin {
                    warningsAsErrors = true
                    optIns.add("kotlin.uuid.ExperimentalUuidApi")
                }
            }
            kotlin {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core"))
                    implementation("tech.annexflow.lvm:lvm-core:1.0.0")
                }
            }
            """,
        )
    }

    @Test
    fun `writes the module's report with where each value comes from`() {
        val fixture = fixture()

        fixture.build(":lib:lvmReport", "-Plvm.kotlin.explicitApi=true")

        assertMatchesExample(fixture.report(":lib"))
    }

    @Test
    fun `writes the same report when the configuration cache is reused`() {
        val fixture = fixture()
        fixture.build(":lib:lvmReport")
        val first = fixture.reportFile(":lib").readText()
        fixture.reportFile(":lib").delete()

        val result = fixture.build(":lib:lvmReport")

        assertContains(result.output, "Reusing configuration cache")
        assertEquals(first, fixture.reportFile(":lib").readText())
    }

    private fun assertMatchesExample(report: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val example = JsonSlurper().parse(checkNotNull(javaClass.getResource("/lvm-report.json"))) as Map<String, Any?>

        assertEquals(LvmReportFields.VERSIONED, example.keys.intersect(LvmReportFields.VERSIONED))
        @Suppress("UNCHECKED_CAST")
        val sourceSets = report.getValue("sourceSets") as List<String>
        assertTrue(sourceSets.containsAll(listOf("commonMain", "commonTest", "jvmMain", "iosMain")), "$sourceSets")
        @Suppress("UNCHECKED_CAST")
        val tasks = report.getValue("tasks") as List<String>
        assertTrue(tasks.containsAll(listOf("lvmReport", "jvmTest", "linkReleaseFrameworkIosArm64")), "$tasks")
        @Suppress("UNCHECKED_CAST")
        val toolchain = report.getValue("toolchain") as Map<String, Map<String, String>>
        assertEquals(setOf("gradle", "kotlin"), toolchain.keys)
        assertEquals(kotlinVersion, toolchain.getValue("kotlin")["actual"])

        // Everything else is the same as in the example.
        assertEquals(example.withoutVersioned(), report.withoutVersioned())
    }

    private fun Map<String, Any?>.withoutVersioned(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val settings = getValue("settings") as Map<String, Any?>
        return this - LvmReportFields.VERSIONED + ("settings" to settings - "catalogVersion")
    }

    private fun GradleFixture.reportFile(path: String): File =
        rootDir.resolve("build/lvm/report/${path.removePrefix(":").replace(':', '/')}/project.json")

    @Suppress("UNCHECKED_CAST")
    private fun GradleFixture.report(path: String): Map<String, Any?> = JsonSlurper().parse(reportFile(path)) as Map<String, Any?>
}

private object LvmReportFields {
    /** Fields whose content depends on the versions of LVM, Gradle and Kotlin. */
    val VERSIONED = setOf("lvmVersion", "toolchain", "sourceSets", "tasks")
}
