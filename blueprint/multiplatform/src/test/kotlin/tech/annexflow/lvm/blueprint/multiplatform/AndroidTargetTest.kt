package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class AndroidTargetTest {

    @TempDir
    lateinit var dir: File

    @BeforeTest
    fun requireAndroidSdk() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
    }

    private fun module(lvmBody: String) = """
        plugins { id("tech.annexflow.lvm.multiplatform") }
        group = "com.example"
        lvm {
            targets { android() }
            $lvmBody
        }
        extensions.getByType<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
            println("lvm-android namespace=" + variant.namespace.get() + " minSdk=" + variant.minSdk.apiLevel)
        }
    """

    @Test
    fun `derives the namespace and uses default SDK levels`() {
        val fixture = kmpFixture(dir, module(""), modulePath = ":feature:user-profile", withAgp = true)
        fixture.writeLocalProperties()

        val output = fixture.build(":feature:user-profile:help").output

        assertContains(output, "lvm-android namespace=com.example.feature.user_profile minSdk=24")
    }

    @Test
    fun `module values win over settings values`() {
        val fixture = kmpFixture(
            dir,
            module("android { namespace = \"com.example.custom\" }"),
            settingsLvm = "catalog = false\nandroid { minSdk = 26 }",
            withAgp = true,
        )
        fixture.writeLocalProperties()

        val output = fixture.build(":lib:help").output

        assertContains(output, "lvm-android namespace=com.example.custom minSdk=26")
    }

    @Test
    fun `host tests can be switched off`() {
        val fixture = kmpFixture(dir, module(""), withAgp = true)
        fixture.writeLocalProperties()
        assertContains(fixture.build(":lib:tasks", "--all").output, "testAndroidHostTest")

        fixture.file(
            "lib/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.multiplatform") }
            group = "com.example"
            lvm { targets { android { hostTests = false } } }
            """,
        )
        val output = fixture.build(":lib:tasks", "--all").output
        kotlin.test.assertFalse(output.contains("testAndroidHostTest"))
    }
}
