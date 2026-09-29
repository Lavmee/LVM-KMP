package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class CompilerOptionsTest {

    @TempDir
    lateinit var dir: File

    private val helloClass: File get() = dir.resolve("lib/build/classes/kotlin/jvm/main/Hello.class")

    @Test
    fun `jvm target follows settings, then the Gradle property, then the module`() {
        val fixture = kmpFixture(dir, JVM_MODULE, settingsLvm = "catalog = false\njvm { target = 17 }")

        fixture.build(":lib:compileKotlinJvm")
        assertEquals(61, classFileMajorVersion(helloClass))

        fixture.build(":lib:compileKotlinJvm", "-Plvm.jvm.target=11")
        assertEquals(55, classFileMajorVersion(helloClass))

        fixture.file(
            "lib/build.gradle.kts",
            """
            plugins { id("tech.annexflow.lvm.multiplatform") }
            lvm {
                targets { jvm() }
                jvm { target = 21 }
            }
            """,
        )
        fixture.build(":lib:compileKotlinJvm", "-Plvm.jvm.target=11")
        assertEquals(65, classFileMajorVersion(helloClass))
    }

    @Test
    fun `java sources of the jvm target use the jvm target too`() {
        val fixture = kmpFixture(dir, JVM_MODULE)
        fixture.file("lib/src/jvmMain/java/J.java", "public class J {}")

        // 17 differs from the JDK 21 that runs the fixtures, so the Java task can't just keep the JDK's target.
        fixture.build(":lib:compileJvmMainJava", ":lib:compileKotlinJvm", "-Plvm.jvm.target=17")
        assertEquals(61, classFileMajorVersion(dir.resolve("lib/build/classes/java/jvmMain/J.class")))
        assertEquals(61, classFileMajorVersion(helloClass))
    }

    @Test
    fun `warningsAsErrors from a Gradle property fails the build on a warning`() {
        val fixture = kmpFixture(dir, JVM_MODULE)
        // An unused variable is no longer a compiler warning under K2; an unnecessary `!!` is.
        fixture.file("lib/src/commonMain/kotlin/Warning.kt", "fun warning(value: String): String = value!!")

        fixture.build(":lib:compileKotlinJvm")
        val result = fixture.buildAndFail(":lib:compileKotlinJvm", "-Plvm.kotlin.warningsAsErrors=true")
        assertContains(result.output, "-Werror")
    }

    @Test
    fun `opt-ins from settings and the module are combined`() {
        val fixture = kmpFixture(
            dir,
            module = """
                plugins { id("tech.annexflow.lvm.multiplatform") }
                lvm {
                    targets { jvm() }
                    kotlin { optIns.add("FromModule") }
                }
            """,
            settingsLvm = "catalog = false\nkotlin { optIns.add(\"FromSettings\") }",
        )
        fixture.file(
            "lib/src/commonMain/kotlin/OptIns.kt",
            """
            @RequiresOptIn
            annotation class FromSettings

            @RequiresOptIn
            annotation class FromModule

            @FromSettings
            fun settingsApi(): Int = 1

            @FromModule
            fun moduleApi(): Int = 2

            fun useBoth(): Int = settingsApi() + moduleApi()
            """,
        )

        fixture.build(":lib:compileKotlinJvm")

        fixture.file("lib/build.gradle.kts", JVM_MODULE)
        val result = fixture.buildAndFail(":lib:compileKotlinJvm")
        assertContains(result.output, "FromModule")
    }

    @Test
    fun `explicit API applies to main compilations only`() {
        val fixture = kmpFixture(
            dir,
            module = """
                plugins { id("tech.annexflow.lvm.multiplatform") }
                lvm {
                    targets { jvm() }
                    kotlin { explicitApi = true }
                }
            """,
        )
        fixture.file("lib/src/commonMain/kotlin/Hello.kt", "public class Hello")
        fixture.file("lib/src/commonMain/kotlin/Api.kt", "public fun hello(): Int = 1")
        fixture.file("lib/src/commonTest/kotlin/ApiTest.kt", "class ApiTest { fun check() = hello() }")

        fixture.build(":lib:compileTestKotlinJvm")

        fixture.file("lib/src/commonMain/kotlin/Api.kt", "fun hello(): Int = 1")
        val result = fixture.buildAndFail(":lib:compileKotlinJvm")
        assertContains(result.output, "Visibility must be specified in explicit API mode")
    }

    @Test
    fun `kotlin-test is added to commonTest unless disabled`() {
        val fixture = kmpFixture(dir, JVM_MODULE)
        fixture.file(
            "lib/src/commonTest/kotlin/HelloTest.kt",
            """
            import kotlin.test.Test

            class HelloTest {
                @Test
                fun works() = Unit
            }
            """,
        )

        fixture.build(":lib:compileTestKotlinJvm")
        val result = fixture.buildAndFail(":lib:compileTestKotlinJvm", "-Plvm.kotlin.testDependencies=false")
        assertContains(result.output, "Unresolved reference")
    }
}
