package tech.annexflow.lvm.blueprint.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.common.LvmBuildValues
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.detektVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

/** A consumer's own plugin on top of blueprint: it reads lvmValues and configures the quality section. */
class BuildLogicTest {

    @TempDir
    lateinit var dir: File

    private fun fixture(): GradleFixture = GradleFixture(dir).apply {
        file(
            "settings.gradle.kts",
            settingsHeader(
                """
                catalog = false
                android { minSdk = 26 }
                jvm { target = 17 }
                kotlin { optIns.add("a.B") }
                """.trimIndent(),
                buildLogic = true,
            ) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")",
        )
        file(
            "build.gradle.kts",
            """
            plugins {
                id("io.gitlab.arturbosch.detekt") version "$detektVersion" apply false
                id("tech.annexflow.lvm.quality") apply false
            }
            """,
        )
        buildLogic(
            """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import tech.annexflow.lvm.blueprint.common.lvm
            import tech.annexflow.lvm.blueprint.common.lvmValues
            import tech.annexflow.lvm.blueprint.quality.quality

            class ConsumerPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val values = project.lvmValues
                    // Read when the plugin is applied, as plugins that pass plain values to AGP do.
                    val minSdk = values.android.minSdk.get()
                    val line = values.jvm.target.zip(values.kotlin.optIns) { target, optIns ->
                        "lvmValues minSdk=${'$'}minSdk jvmTarget=${'$'}target optIns=${'$'}optIns"
                    }
                    project.tasks.register("printValues") {
                        doLast { println(line.get()) }
                    }
                    project.pluginManager.withPlugin("tech.annexflow.lvm.quality") {
                        project.lvm.quality { config.add("config/detekt/consumer.yml") }
                    }
                }
            }
            """,
            LvmBuildValues::class.java,
            QualitySection::class.java,
        )
        file(
            "lib/build.gradle.kts",
            """
            plugins {
                id("consumer")
                id("tech.annexflow.lvm.quality")
            }
            lvm { jvm { target = 11 } }
            """,
        )
        file("lib/src/Answer.kt", "fun answer(): Int = 42 * 3")
        file(
            "config/detekt/consumer.yml",
            """
            style:
              MagicNumber:
                active: false
            """,
        )
    }

    @Test
    fun `build logic reads the build-wide values and not the module's`() {
        val fixture = fixture()

        assertContains(fixture.build(":lib:printValues").output, "lvmValues minSdk=26 jvmTarget=17 optIns=[a.B]")
        assertContains(
            fixture.build(":lib:printValues", "-Plvm.android.minSdk=28").output,
            "lvmValues minSdk=28 jvmTarget=17 optIns=[a.B]",
        )
    }

    @Test
    fun `build logic configures the quality section through its accessor`() {
        // Without the config the build logic adds, MagicNumber fails detekt on the module's code.
        fixture().build(":lib:detekt")
    }
}
