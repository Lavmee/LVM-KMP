package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
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

    /** A module with the flat layout on whose only sources are [files], by path in the module. */
    private fun javaFixture(module: String, files: Map<String, String>, withAgp: Boolean = false): GradleFixture =
        kmpFixture(dir, module, settingsLvm = FLAT_SETTINGS, withAgp = withAgp).apply {
            dir.resolve("lib/src/commonMain").deleteRecursively()
            for ((path, content) in files) file("lib/$path", content)
        }

    @Test
    fun `the flat layout compiles Java files of a JVM target`() {
        val fixture = javaFixture(
            JVM_MODULE,
            mapOf(
                "src@jvm/NativeMarkdown.java" to NATIVE_MARKDOWN,
                "src@jvm/Markdown.kt" to MARKDOWN,
                "test@jvm/Heading.java" to HEADING,
                "test@jvm/MarkdownTest.kt" to MARKDOWN_TEST,
            ),
        )

        // Kotlin compiles against the Java files either way; without javac, the test fails with NoClassDefFoundError.
        fixture.build(":lib:jvmTest")

        assertTrue(dir.resolve("lib/build/test-results/jvmTest/TEST-MarkdownTest.xml").isFile)
    }

    @Test
    fun `the flat layout rejects Java files of a source set without Java`() {
        val fixture = javaFixture(JVM_MODULE, mapOf("src/NativeMarkdown.java" to NATIVE_MARKDOWN, "src/Markdown.kt" to MARKDOWN))

        val output = fixture.buildAndFail(":lib:compileKotlinJvm").output

        assertContains(output, "src/NativeMarkdown.java")
    }

    @Test
    fun `the configuration cache looks for Java files again once one is added`() {
        val fixture = javaFixture(JVM_MODULE, mapOf("src/Markdown.kt" to "class Markdown"))
        fixture.build(":lib:compileKotlinJvm")
        fixture.file("lib/src/NativeMarkdown.java", NATIVE_MARKDOWN)

        val output = fixture.buildAndFail(":lib:compileKotlinJvm").output

        assertContains(output, "src/NativeMarkdown.java")
    }

    @Test
    fun `the flat layout compiles Java files of the Android target after withJava`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = javaFixture(
            androidModule("kotlin { android { withJava() } }"),
            mapOf(
                "src@android/NativeMarkdown.java" to NATIVE_MARKDOWN,
                "src@android/Markdown.kt" to MARKDOWN,
                "test@androidHost/Heading.java" to HEADING,
                "test@androidHost/MarkdownTest.kt" to MARKDOWN_TEST,
            ),
            withAgp = true,
        )
        fixture.writeLocalProperties()

        fixture.build(":lib:testAndroidHostTest")

        assertTrue(dir.resolve("lib/build/test-results/testAndroidHostTest/TEST-MarkdownTest.xml").isFile)
    }

    @Test
    fun `the flat layout rejects Java files of the Android target without withJava`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = javaFixture(
            androidModule(""),
            mapOf("src@android/NativeMarkdown.java" to NATIVE_MARKDOWN, "src@android/Markdown.kt" to MARKDOWN),
            withAgp = true,
        )
        fixture.writeLocalProperties()

        val output = fixture.buildAndFail(":lib:compileAndroidMain").output

        assertContains(output, "src@android/NativeMarkdown.java")
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

    private companion object {
        const val FLAT_SETTINGS = "catalog = false\nkotlin { flatLayout = true }"

        const val NATIVE_MARKDOWN =
            "public class NativeMarkdown { public static String render(String text) { return \"<h1>\" + text + \"</h1>\"; } }"

        const val HEADING =
            "public class Heading { public static String of(String text) { return \"<h1>\" + text + \"</h1>\"; } }"

        const val MARKDOWN = "class Markdown { fun render(text: String): String = NativeMarkdown.render(text) }"

        const val MARKDOWN_TEST = """
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class MarkdownTest {
                @Test
                fun rendersThroughJava() {
                    assertEquals(Heading.of("lvm"), Markdown().render("lvm"))
                }
            }
        """

        /** Lists the AGP plugin too, which generates the native kotlin { android { } } accessor that withJava() needs. */
        fun androidModule(body: String) = """
            plugins {
                id("tech.annexflow.lvm.multiplatform")
                id("com.android.kotlin.multiplatform.library")
            }
            group = "com.example"
            lvm { targets { android() } }
            $body
        """
    }
}
