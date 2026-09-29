package tech.annexflow.lvm.blueprint.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

class LvmSettingsPluginTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `forwards settings values to every project under isolated projects`() {
        val fixture = GradleFixture(dir)
        fixture.file(
            "settings.gradle.kts",
            settingsHeader(
                """
                catalog = false
                android { minSdk = 26 }
                kotlin {
                    optIns.add("a.B")
                    optIns.add("c.D")
                }
                """.trimIndent(),
            ) + """
            rootProject.name = "fixture"
            include(":a", ":b:c")
            """.trimIndent(),
        )
        val printer = """
            val line = "lvm " + path + ": minSdk=" + extra["lvm.android.minSdk"] + " optIns=" + extra["lvm.kotlin.optIns"] + " hasCompileSdk=" + extra.has("lvm.android.compileSdk")
            tasks.register("printLvm") {
                // Task actions can't capture script-level values under the configuration cache.
                val text = line
                doLast { println(text) }
            }
        """
        fixture.file("a/build.gradle.kts", printer)
        fixture.file("b/c/build.gradle.kts", printer)

        val result = fixture.build("printLvm", "-Dorg.gradle.unsafe.isolated-projects=true")

        assertContains(result.output, "lvm :a: minSdk=26 optIns=a.B,c.D hasCompileSdk=false")
        assertContains(result.output, "lvm :b:c: minSdk=26 optIns=a.B,c.D hasCompileSdk=false")
    }

    @Test
    fun `adds repositories unless disabled`() {
        val fixture = GradleFixture(dir)
        fixture.file("settings.gradle.kts", settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        fixture.file(
            "lib/build.gradle.kts",
            """
            plugins { `java-library` }
            dependencies { implementation("junit:junit:4.13.2") }
            """,
        )

        val resolved = fixture.build(":lib:dependencies", "--configuration", "compileClasspath")
        assertContains(resolved.output, "junit:junit:4.13.2")
        assertFalse(resolved.output.contains("junit:junit:4.13.2 FAILED"))

        fixture.file(
            "settings.gradle.kts",
            settingsHeader("catalog = false\nrepositories = false") + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")",
        )
        val unresolved = fixture.build(":lib:dependencies", "--configuration", "compileClasspath")
        assertContains(unresolved.output, "junit:junit:4.13.2 FAILED")
    }

    @Test
    fun `creates the catalog from lvm-versions under a configurable name`() {
        val publisher = GradleFixture(dir.resolve("publisher"))
        publisher.file("settings.gradle.kts", "rootProject.name = \"lvm-versions\"")
        publisher.file(
            "build.gradle.kts",
            """
            plugins {
                `version-catalog`
                `maven-publish`
            }
            group = "tech.annexflow.lvm"
            version = "9.9.9"
            catalog { versionCatalog { version("kotlin", "1.2.3") } }
            publishing {
                publications { create<MavenPublication>("maven") { from(components["versionCatalog"]) } }
                repositories { maven { url = uri("../repo") } }
            }
            """,
        )
        publisher.build("publish")

        val consumer = GradleFixture(dir.resolve("consumer"))
        consumer.file(
            "settings.gradle.kts",
            settingsHeader("repositories = false\ncatalogVersion = \"9.9.9\"") + """
            dependencyResolutionManagement {
                repositories { maven { url = uri("../repo") } }
            }
            rootProject.name = "consumer"
            """.trimIndent(),
        )
        consumer.file(
            "build.gradle.kts",
            """
            val kotlinVersion = lvmLibs.versions.kotlin.get()
            tasks.register("printCatalog") {
                val text = "catalog kotlin=" + kotlinVersion
                doLast { println(text) }
            }
            """,
        )
        assertContains(consumer.build("printCatalog").output, "catalog kotlin=1.2.3")

        consumer.file(
            "settings.gradle.kts",
            settingsHeader("repositories = false\ncatalogVersion = \"9.9.9\"\ncatalogName = \"custom\"") + """
            dependencyResolutionManagement {
                repositories { maven { url = uri("../repo") } }
            }
            rootProject.name = "consumer"
            """.trimIndent(),
        )
        consumer.file(
            "build.gradle.kts",
            """
            val kotlinVersion = custom.versions.kotlin.get()
            tasks.register("printCatalog") {
                val text = "catalog kotlin=" + kotlinVersion
                doLast { println(text) }
            }
            """,
        )
        assertContains(consumer.build("printCatalog").output, "catalog kotlin=1.2.3")
    }
}
