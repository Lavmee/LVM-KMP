package tech.annexflow.lvm.blueprint.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class AndroidSourceLayoutTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `maps Android source sets to flat directories`() {
        val directories = listOf(
            "main", "debug", "freeRelease", "test", "testDebug", "androidTest", "androidTestFreeDebug",
            "testFixtures", "testFixturesRelease", "tester",
        ).associateWith(::flatSourceDirectory)

        assertEquals(
            mapOf(
                "main" to "src",
                "debug" to "src@debug",
                "freeRelease" to "src@freeRelease",
                "test" to "test",
                "testDebug" to "test@debug",
                "androidTest" to "androidTest",
                "androidTestFreeDebug" to "androidTest@freeDebug",
                "testFixtures" to "testFixtures",
                "testFixturesRelease" to "testFixtures@release",
                "tester" to "src@tester",
            ),
            directories,
        )
    }

    @Test
    fun `the flat layout compiles sources from src, src@debug and test`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = applicationFixture(dir, "", settingsLvm = FLAT_WITHOUT_CHECK)
        dir.resolve("app/src/main/kotlin").deleteRecursively()
        fixture.file("app/src/Main.kt", "class Main")
        fixture.file("app/src@debug/DebugOnly.kt", "class DebugOnly")
        fixture.file("app/test/MainTest.kt", "class MainTest { @kotlin.test.Test fun works() { Main(); DebugOnly() } }")

        fixture.build(":app:testDebugUnitTest")

        assertTrue(dir.debugClass("Main").isFile)
        assertTrue(dir.debugClass("DebugOnly").isFile)
        assertTrue(dir.resolve("app/build/test-results/testDebugUnitTest/TEST-MainTest.xml").isFile)
    }

    @Test
    fun `the flat layout compiles Java sources that Kotlin calls`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = applicationFixture(dir, "", settingsLvm = FLAT_WITHOUT_CHECK)
        dir.resolve("app/src/main/kotlin").deleteRecursively()
        fixture.file(
            "app/src/NativeMarkdown.java",
            "public class NativeMarkdown { public static String render(String text) { return \"<h1>\" + text + \"</h1>\"; } }",
        )
        fixture.file("app/src/Markdown.kt", "class Markdown { fun render(text: String) = NativeMarkdown.render(text) }")
        // Kotlin compiles against the Java file either way; without javac, the test fails with NoClassDefFoundError.
        fixture.file(
            "app/test/MarkdownTest.kt",
            "class MarkdownTest { @kotlin.test.Test fun renders() { kotlin.test.assertEquals(\"<h1>lvm</h1>\", Markdown().render(\"lvm\")) } }",
        )

        fixture.build(":app:testDebugUnitTest")

        assertTrue(dir.resolve("app/build/test-results/testDebugUnitTest/TEST-MarkdownTest.xml").isFile)
    }

    @Test
    fun `the package check spans every source set and runs before the variants build`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = applicationFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")
        dir.resolve("app/src/main/kotlin").deleteRecursively()
        fixture.file("app/src/Main.kt", "package com.example.app\n\nclass Main")
        fixture.file("app/src@debug/debug/DebugOnly.kt", "package com.example.app.debug\n\nclass DebugOnly")
        fixture.file("app/test/MainTest.kt", "package com.example.app\n\nclass MainTest")
        fixture.build(":app:compileDebugKotlin")
        fixture.file("app/androidTest@debug/MainScreenTest.kt", "package com.example.app.ui\n\nclass MainScreenTest")

        val output = fixture.buildAndFail(":app:compileDebugKotlin").output

        assertTrue(dir.debugClass("com/example/app/debug/DebugOnly").isFile)
        assertContains(output, "androidTest@debug/MainScreenTest.kt")
    }

    @Test
    fun `the flat layout fails while conventional source directories remain`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = applicationFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        val output = fixture.buildAndFail(":app:help").output

        assertContains(output, "src/main/kotlin")
    }

    @Test
    fun `a flavor's flat directories compile into that flavor's variants only`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = libraryFixture(
            dir,
            """
            android {
                flavorDimensions += "tier"
                productFlavors {
                    register("free") { dimension = "tier" }
                    register("paid") { dimension = "tier" }
                }
            }
            """,
            settingsLvm = FLAT_WITHOUT_CHECK,
        )
        dir.resolve("core/ui/src/main/kotlin").deleteRecursively()
        fixture.file("core/ui/src/Main.kt", "class Main")
        fixture.file("core/ui/src@paid/PaidOnly.kt", "class PaidOnly")
        fixture.file("core/ui/test/MainTest.kt", "class MainTest { @kotlin.test.Test fun works() { Main() } }")
        fixture.file("core/ui/test@paid/PaidTest.kt", "class PaidTest { @kotlin.test.Test fun works() { PaidOnly() } }")

        fixture.build(":core:ui:testFreeDebugUnitTest", ":core:ui:testPaidDebugUnitTest")

        val results = dir.resolve("core/ui/build/test-results")
        assertTrue(dir.libraryClass("Main", "freeDebug").isFile)
        assertTrue(dir.libraryClass("PaidOnly", "paidDebug").isFile)
        assertFalse(dir.libraryClass("PaidOnly", "freeDebug").exists())
        assertTrue(results.resolve("testFreeDebugUnitTest/TEST-MainTest.xml").isFile)
        assertTrue(results.resolve("testPaidDebugUnitTest/TEST-PaidTest.xml").isFile)
        assertFalse(results.resolve("testFreeDebugUnitTest/TEST-PaidTest.xml").exists())
    }

    @Test
    fun `a library with the flat layout fails while conventional source directories remain`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = libraryFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        val output = fixture.buildAndFail(":core:ui:help").output

        assertContains(output, "src/main/kotlin")
    }

    private companion object {
        /** The flat layout without lvmCheckSourceLayout, which rejects the fixture's files: they declare no package. */
        const val FLAT_WITHOUT_CHECK = "catalog = false\nkotlin {\n    flatLayout = true\n    checkSourceLayout = false\n}"
    }
}
