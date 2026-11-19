package tech.annexflow.lvm.blueprint.android

import groovy.json.JsonSlurper
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class LvmAndroidTestPluginTest {

    @TempDir
    lateinit var dir: File

    @BeforeTest
    fun requireAndroidSdk() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
    }

    @Test
    fun `uses default SDK levels, derives the namespace, turns off animations and leaves out duplicate metadata, without desugaring`() {
        val output = testModuleFixture(dir, PRINT_TEST_MODULE_DSL).build(":benchmark:help").output

        assertContains(
            output,
            "lvm-dsl namespace=com.example.benchmark compileSdk=37 minSdk=24 targetSdk=37 " +
                "runner=androidx.test.runner.AndroidJUnitRunner",
        )
        assertContains(output, "lvm-dsl desugaring=false animationsDisabled=true excludes=[/META-INF/DEPENDENCIES, /META-INF/INDEX.LIST]")
    }

    @Test
    fun `native DSL values win over module values, which win over settings values`() {
        val fixture = testModuleFixture(
            dir,
            """
            android {
                namespace = "com.example.nativedsl"
                compileSdk = 36
                defaultConfig.testInstrumentationRunner = "com.example.NativeRunner"
                testOptions.animationsDisabled = false
            }
            lvm { android { targetSdk = 36; testInstrumentationRunner = "com.example.LvmRunner" } }
            """ + PRINT_TEST_MODULE_DSL,
            settingsLvm = "catalog = false\nandroid { minSdk = 28; targetSdk = 35 }",
        )

        val output = fixture.build(":benchmark:help").output

        assertContains(
            output,
            "lvm-dsl namespace=com.example.nativedsl compileSdk=36 minSdk=28 targetSdk=36 runner=com.example.NativeRunner",
        )
        assertContains(output, "animationsDisabled=false")
    }

    @Test
    fun `the minor level of the compile SDK reaches AGP, which otherwise uses minor level 0`() {
        val fixture = testModuleFixture(dir, PRINT_COMPILE_SDK)

        assertEquals("android-37.0", compileSdkIn(fixture.build(":benchmark:help").output))
        assertEquals("android-37.1", compileSdkIn(fixture.build(":benchmark:help", "-Plvm.android.compileSdkMinor=1").output))
    }

    @Test
    fun `the Java version follows lvm values, and Kotlin follows it`() {
        val fixture = testModuleFixture(dir, "")

        fixture.build(":benchmark:compileDebugKotlin")
        assertEquals(65, classFileMajorVersion(dir.testModuleClass("Hello")))

        fixture.build(":benchmark:compileDebugKotlin", "-Plvm.jvm.target=17")
        assertEquals(61, classFileMajorVersion(dir.testModuleClass("Hello")))

        fixture.file("benchmark/build.gradle.kts", TEST_MODULE_HEADER + "lvm { jvm { target = 17 } }")
        fixture.build(":benchmark:compileDebugKotlin", "-Plvm.jvm.target=21")
        assertEquals(61, classFileMajorVersion(dir.testModuleClass("Hello")))
    }

    @Test
    fun `the flat layout compiles sources from src and src@debug`() {
        val fixture = testModuleFixture(dir, "", settingsLvm = "catalog = false\nkotlin { flatLayout = true }")
        dir.resolve("benchmark/src/main/kotlin").deleteRecursively()
        fixture.file("benchmark/src/StartupBenchmark.kt", "class StartupBenchmark")
        fixture.file("benchmark/src@debug/DebugOnly.kt", "class DebugOnly { val benchmark = StartupBenchmark() }")

        fixture.build(":benchmark:compileDebugKotlin")

        assertTrue(dir.testModuleClass("StartupBenchmark").isFile)
        assertTrue(dir.testModuleClass("DebugOnly").isFile)
    }

    @Test
    fun `the report has the plugin and the Android values with their sources`() {
        val fixture = testModuleFixture(dir, "lvm { android { minSdk = 28 } }")

        fixture.build(":benchmark:lvmReport")

        @Suppress("UNCHECKED_CAST")
        val report = JsonSlurper().parse(dir.resolve("build/lvm/report/benchmark/project.json")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val values = report.getValue("values") as Map<String, Map<String, Any?>>
        assertEquals(listOf("tech.annexflow.lvm.android.test"), report["plugins"])
        assertEquals(mapOf("value" to 28, "source" to "module"), values["android.minSdk"])
        assertEquals(mapOf("value" to "com.example.benchmark", "source" to "default"), values["android.namespace"])
        assertEquals(mapOf("value" to 21, "source" to "default"), values["jvm.target"])
    }
}
