package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.testing.GradleFixture

class AndroidTargetTest {

    @TempDir
    lateinit var dir: File

    private val helloClass: File get() = dir.resolve("lib/build/classes/kotlin/android/main/Hello.class")

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

    /** Lists the AGP plugin too, which is what generates the native kotlin { android { } } and androidComponents { } accessors. */
    private fun moduleWithAgpPlugin(body: String) = """
        plugins {
            id("tech.annexflow.lvm.multiplatform")
            id("com.android.kotlin.multiplatform.library")
        }
        group = "com.example"
        lvm { targets { android() } }
        $body
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
    fun `native DSL values win when the module lists the AGP plugin`() {
        val fixture = kmpFixture(
            dir,
            moduleWithAgpPlugin(
                """
                kotlin { android { minSdk = 30 } }
                androidComponents.onVariants { variant ->
                    println("lvm-android namespace=" + variant.namespace.get() + " minSdk=" + variant.minSdk.apiLevel)
                }
                """,
            ),
            withAgp = true,
        )
        fixture.writeLocalProperties()

        val output = fixture.build(":lib:help").output

        assertContains(output, "lvm-android namespace=com.example.lib minSdk=30")
    }

    @Test
    fun `a native preview compile SDK is left alone`() {
        val fixture = kmpFixture(
            dir,
            moduleWithAgpPlugin(
                """
                kotlin { android { compileSdkPreview = "LvmPreview" } }
                // finalizeDsl callbacks run in registration order, so this one sees what lvm's callback left.
                androidComponents.finalizeDsl { android ->
                    println("lvm-android compileSdk=" + android.compileSdk + " compileSdkPreview=" + android.compileSdkPreview)
                }
                """,
            ),
            withAgp = true,
        )
        fixture.writeLocalProperties()

        val output = fixture.build(":lib:help").output

        assertContains(output, "lvm-android compileSdk=null compileSdkPreview=LvmPreview")
    }

    @Test
    fun `compiles for the lvm jvm target`() {
        val fixture = kmpFixture(dir, module(""), withAgp = true)
        fixture.writeLocalProperties()

        fixture.build(":lib:compileAndroidMain")
        assertEquals(65, classFileMajorVersion(helloClass))

        fixture.build(":lib:compileAndroidMain", "-Plvm.jvm.target=17")
        assertEquals(61, classFileMajorVersion(helloClass))
    }

    @Test
    fun `a native jvm target wins when the module lists the AGP plugin`() {
        val fixture = kmpFixture(
            dir,
            moduleWithAgpPlugin(
                "kotlin { android { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } } }",
            ),
            withAgp = true,
        )
        fixture.writeLocalProperties()

        // lvm's default is 21.
        fixture.build(":lib:compileAndroidMain")
        assertEquals(61, classFileMajorVersion(helloClass))
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
