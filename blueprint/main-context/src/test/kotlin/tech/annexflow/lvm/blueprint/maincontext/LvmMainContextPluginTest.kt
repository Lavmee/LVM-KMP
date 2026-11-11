package tech.annexflow.lvm.blueprint.maincontext

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

class LvmMainContextPluginTest {

    @TempDir
    lateinit var dir: File

    private val compiler = "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_COMPILER_ARTIFACT}:${LvmBuildInfo.VERSION}"
    private val annotation = "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_ARTIFACT}:${LvmBuildInfo.VERSION}"

    @Test
    fun `platforms follow settings, then the Gradle property, then the module`() {
        val fixture = fixture(settingsLvm = "catalog = false\nmainContext { platforms.add(\"ios\") }")

        assertEquals(setOf("IosArm64Main", "IosArm64Test"), fixture.compilationsWithCompiler())
        assertEquals(setOf("JvmMain", "JvmTest"), fixture.compilationsWithCompiler("-Plvm.mainContext.platforms=jvm"))

        fixture.file("lib/build.gradle.kts", module("lvm { mainContext { platforms.set(listOf(\"ios\", \"jvm\")) } }"))
        assertEquals(
            setOf("IosArm64Main", "IosArm64Test", "JvmMain", "JvmTest"),
            fixture.compilationsWithCompiler("-Plvm.mainContext.platforms=jvm"),
        )
    }

    @Test
    fun `an unknown platform fails the build`() {
        val result = fixture().buildAndFail(":lib:printMainContext", "-Plvm.mainContext.platforms=jvm,web")

        assertContains(result.output, "'web'")
    }

    @Test
    fun `adds the annotation to commonMain unless switched off`() {
        val fixture = fixture()

        assertContains(fixture.build(":lib:printMainContext").output, "commonMain=$annotation")
        assertContains(fixture.build(":lib:printMainContext", "-Plvm.mainContext.dependency=false").output, "commonMain=\n")
    }

    private fun fixture(settingsLvm: String = "catalog = false"): GradleFixture = GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
                id("tech.annexflow.lvm.main-context") apply false
            }
            """,
        )
        file("lib/build.gradle.kts", module())
    }

    /** A module with JVM and iOS targets, [extra] lines and a task that prints what the plugin added. */
    private fun module(extra: String = ""): String = """
        plugins {
            id("tech.annexflow.lvm.multiplatform")
            id("tech.annexflow.lvm.main-context")
        }
        lvm {
            targets {
                jvm()
                ios { architectures.set(listOf("iosArm64")) }
            }
        }
        $extra
        val prefix = "kotlinCompilerPluginClasspath"
        val lines = provider {
            fun coordinates(configuration: Configuration) =
                configuration.dependencies.joinToString { listOfNotNull(it.group, it.name, it.version).joinToString(":") }
            val compilations = configurations.filter { it.name.startsWith(prefix) }
                .map { it.name.removePrefix(prefix) + "=" + coordinates(it) }
            compilations + ("commonMain=" + coordinates(configurations.getByName("commonMainImplementation")))
        }
        tasks.register("printMainContext") {
            val text = lines
            doLast { text.get().forEach(::println) }
        }
    """

    /** The compilations whose compiler plugin classpath has the main-context compiler plugin. */
    private fun GradleFixture.compilationsWithCompiler(vararg arguments: String): Set<String> =
        build(":lib:printMainContext", *arguments).output.lines()
            .filter { line -> compiler in line.substringAfter('=').split(", ") }
            .map { line -> line.substringBefore('=') }
            .toSet()
}
