package tech.annexflow.lvm.blueprint.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.detektVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

class LvmQualityPluginTest {

    @TempDir
    lateinit var dir: File

    private fun fixture(settingsLvm: String = "catalog = false"): GradleFixture = GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("io.gitlab.arturbosch.detekt") version "$detektVersion" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
                id("tech.annexflow.lvm.quality") apply false
            }
            """,
        )
        file(
            "lib/build.gradle.kts",
            """
            plugins {
                id("tech.annexflow.lvm.multiplatform")
                id("tech.annexflow.lvm.quality")
            }
            lvm { targets { jvm() } }
            """,
        )
        file("lib/src/commonMain/kotlin/Answer.kt", "fun answer(): Int = 42 * 3")
    }

    @Test
    fun `analyzes multiplatform sources`() {
        val result = fixture().buildAndFail(":lib:detekt")

        assertContains(result.output, "MagicNumber")
        assertContains(result.output, "commonMain")
    }

    @Test
    fun `uses build-wide config files`() {
        val fixture = fixture("catalog = false\nquality { config.add(\"config/detekt/detekt.yml\") }")
        fixture.file(
            "config/detekt/detekt.yml",
            """
            style:
              MagicNumber:
                active: false
            """,
        )

        fixture.build(":lib:detekt")
    }

    @Test
    fun `adds Compose rules only when enabled`() {
        val fixture = fixture()

        val without = fixture.build(":lib:dependencies", "--configuration", "detektPlugins").output
        assertFalse(without.contains("ru.kode:detekt-rules-compose"))

        val with = fixture.build(":lib:dependencies", "--configuration", "detektPlugins", "-Plvm.quality.composeRules=true").output
        assertContains(with, "ru.kode:detekt-rules-compose:1.4.0")
    }
}
