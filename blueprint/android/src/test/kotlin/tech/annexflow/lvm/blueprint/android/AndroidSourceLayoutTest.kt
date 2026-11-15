package tech.annexflow.lvm.blueprint.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        val fixture = applicationFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")
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
    fun `the flat layout fails while conventional source directories remain`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = applicationFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")

        val output = fixture.buildAndFail(":app:help").output

        assertContains(output, "src/main/kotlin")
    }
}
