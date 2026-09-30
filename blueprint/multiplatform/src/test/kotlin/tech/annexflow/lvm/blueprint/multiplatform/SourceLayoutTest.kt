package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class SourceLayoutTest {

    @TempDir
    lateinit var dir: File

    /** A jvm module whose sources sit in the flat layout's directories instead of the fixture's src/commonMain. */
    private fun flatFixture(settingsLvm: String = "catalog = false", module: String = JVM_MODULE): GradleFixture =
        kmpFixture(dir, module, settingsLvm = settingsLvm).apply {
            dir.resolve("lib/src/commonMain").deleteRecursively()
            file("lib/src/Hello.kt", "class Hello")
            file("lib/src@jvm/JvmHello.kt", "fun jvmHello(): Hello = Hello()")
            file(
                "lib/test/HelloTest.kt",
                """
                import kotlin.test.Test
                import kotlin.test.assertEquals

                class HelloTest {
                    @Test
                    fun jvmHelloCreatesHello() {
                        assertEquals("Hello", jvmHello()::class.simpleName)
                    }
                }
                """,
            )
        }

    @Test
    fun `settings can switch every module to the flat layout`() {
        val fixture = flatFixture(settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        fixture.build(":lib:compileTestKotlinJvm")

        assertTrue(dir.resolve("lib/build/classes/kotlin/jvm/main/Hello.class").isFile)
        assertTrue(dir.resolve("lib/build/classes/kotlin/jvm/main/JvmHelloKt.class").isFile)
        assertTrue(dir.resolve("lib/build/classes/kotlin/jvm/test/HelloTest.class").isFile)
    }

    @Test
    fun `a module can turn the flat layout on after declaring its targets`() {
        val fixture = flatFixture(
            module = """
                plugins { id("tech.annexflow.lvm.multiplatform") }
                lvm {
                    targets { jvm() }
                    kotlin { flatLayout = true }
                }
            """,
        )

        fixture.build(":lib:compileKotlinJvm")

        assertTrue(dir.resolve("lib/build/classes/kotlin/jvm/main/JvmHelloKt.class").isFile)
    }

    @Test
    fun `the flat layout rejects a module that still has conventional source directories`() {
        val fixture = kmpFixture(dir, JVM_MODULE, settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        val output = fixture.buildAndFail(":lib:compileKotlinJvm").output

        assertContains(output, "src/commonMain/kotlin")
    }

    @Test
    fun `flat directories are named after their source sets`() {
        assertEquals("src", flatSourceDirectory("commonMain"))
        assertEquals("test", flatSourceDirectory("commonTest"))
        assertEquals("src@jvm", flatSourceDirectory("jvmMain"))
        assertEquals("src@iosSimulatorArm64", flatSourceDirectory("iosSimulatorArm64Main"))
        assertEquals("test@androidHost", flatSourceDirectory("androidHostTest"))
        assertNull(flatSourceDirectory("custom"))
    }
}
