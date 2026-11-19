package tech.annexflow.lvm.blueprint.android

import groovy.json.JsonSlurper
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class LvmAndroidReportTest {

    @TempDir
    lateinit var dir: File

    @BeforeTest
    fun requireAndroidSdk() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
    }

    @Test
    fun `the report of an application has its Android values with their sources`() {
        val fixture = applicationFixture(
            dir,
            """
            lvm { application { applicationId = "com.example.custom" } }
            """,
            settingsLvm = "catalog = false\nandroid { minSdk = 26; compileSdkMinor = 1 }\napplication { versionCode = 2 }",
        )

        fixture.build(":app:lvmReport", "-Plvm.application.versionName=2.0")

        @Suppress("UNCHECKED_CAST")
        val report = JsonSlurper().parse(dir.resolve("build/lvm/report/app/project.json")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val values = report.getValue("values") as Map<String, Map<String, Any?>>
        assertEquals(listOf("tech.annexflow.lvm.android.application"), report["plugins"])
        assertEquals(mapOf("value" to 37, "source" to "default"), values["android.compileSdk"])
        assertEquals(mapOf("value" to 1, "source" to "settings"), values["android.compileSdkMinor"])
        assertEquals(mapOf("value" to 26, "source" to "settings"), values["android.minSdk"])
        assertEquals(mapOf("value" to "com.example.app", "source" to "default"), values["android.namespace"])
        assertEquals(mapOf("value" to "com.example.custom", "source" to "module"), values["application.applicationId"])
        assertEquals(mapOf("value" to 2, "source" to "settings"), values["application.versionCode"])
        assertEquals(mapOf("value" to "2.0", "source" to "property"), values["application.versionName"])
        @Suppress("UNCHECKED_CAST")
        val toolchain = report.getValue("toolchain") as Map<String, Any?>
        assertEquals(setOf("agp", "gradle", "kotlin"), toolchain.keys)
        @Suppress("UNCHECKED_CAST")
        val sourceSets = report.getValue("sourceSets") as List<String>
        assertTrue(sourceSets.containsAll(listOf("main", "test", "androidTest", "debug", "release")), "$sourceSets")
    }
}
