package tech.annexflow.lvm.blueprint.jvm

import groovy.json.JsonSlurper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir

class LvmJvmPluginTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `Kotlin and Java compile for the Java version of lvm values, native java options and native jvmTarget`() {
        val fixture = jvmFixture(dir)
        fixture.file("lib/src/main/java/Greeter.java", "public class Greeter {}")
        fun versions() = classFileMajorVersion(dir.mainClass("Hello")) to classFileMajorVersion(dir.mainClass("Greeter", "java"))

        fixture.build(":lib:classes")
        assertEquals(65 to 65, versions())

        fixture.build(":lib:classes", "-Plvm.jvm.target=17")
        assertEquals(61 to 61, versions())

        fixture.file("lib/build.gradle.kts", JVM_MODULE_HEADER + "lvm { jvm { target = 17 } }")
        fixture.build(":lib:classes")
        assertEquals(61 to 61, versions())

        fixture.file(
            "lib/build.gradle.kts",
            JVM_MODULE_HEADER + "java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }",
        )
        fixture.build(":lib:classes")
        assertEquals(61 to 61, versions())

        fixture.file(
            "lib/build.gradle.kts",
            JVM_MODULE_HEADER + "kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }",
        )
        fixture.build(":lib:classes")
        assertEquals(61 to 61, versions())
    }

    @Test
    fun `passes lvm kotlin options to the compiler, with explicit API mode for main code only`() {
        val fixture = jvmFixture(
            dir,
            """
            lvm {
                kotlin {
                    explicitApi = true
                    optIns.add("kotlin.ExperimentalStdlibApi")
                    freeCompilerArgs.add("-Xsuppress-version-warnings")
                }
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                val options = compilerOptions
                val taskName = name
                doFirst {
                    println(
                        "lvm-kotlin " + taskName + " warningsAsErrors=" + options.allWarningsAsErrors.get() +
                            " optIn=" + options.optIn.get() + " args=" + options.freeCompilerArgs.get(),
                    )
                }
            }
            """,
            settingsLvm = "catalog = false\nkotlin { warningsAsErrors = true }",
        )
        fixture.file("lib/src/test/kotlin/HelloTest.kt", "class HelloTest { @kotlin.test.Test fun works() { Hello() } }")

        val implicitVisibility = fixture.buildAndFail(":lib:compileKotlin")
        fixture.file("lib/src/main/kotlin/Hello.kt", "public class Hello")
        val lines = fixture.build(":lib:test").output.lines()

        assertEquals(TaskOutcome.FAILED, implicitVisibility.task(":lib:compileKotlin")?.outcome)
        val main = lines.single { it.startsWith("lvm-kotlin compileKotlin ") }
        val test = lines.single { it.startsWith("lvm-kotlin compileTestKotlin ") }
        assertContains(main, "warningsAsErrors=true optIn=[kotlin.ExperimentalStdlibApi] args=[-Xsuppress-version-warnings")
        assertContains(main, "-Xexplicit-api=strict")
        assertContains(test, "warningsAsErrors=true optIn=[kotlin.ExperimentalStdlibApi]")
        assertFalse(test.contains("-Xexplicit-api"), test)
        assertTrue(dir.resolve("lib/build/test-results/test/TEST-HelloTest.xml").isFile)
    }

    @Test
    fun `jvmDefault reaches the compiler, Kotlin's own default stays while it is unset and native DSL wins`() {
        val fixture = jvmFixture(dir)
        fixture.file("lib/src/main/kotlin/Greeter.kt", "interface Greeter { fun greet(): String = \"hello\" }")
        val defaultImpls = dir.mainClass("Greeter\$DefaultImpls")

        fixture.build(":lib:classes")
        assertTrue(defaultImpls.isFile)

        val invalid = fixture.buildAndFail(":lib:classes", "-Plvm.kotlin.jvmDefault=all")
        assertContains(invalid.output, "lvm.kotlin.jvmDefault")

        fixture.file("lib/build.gradle.kts", JVM_MODULE_HEADER + "lvm { kotlin { jvmDefault = \"no-compatibility\" } }")
        fixture.build(":lib:classes")
        assertFalse(defaultImpls.exists())

        fixture.file(
            "lib/build.gradle.kts",
            JVM_MODULE_HEADER + "kotlin { compilerOptions { jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.ENABLE } }",
        )
        fixture.build(":lib:classes", "-Plvm.kotlin.jvmDefault=no-compatibility")
        assertTrue(defaultImpls.isFile)
    }

    @Test
    fun `adds kotlin-test in the variant of the test framework, unless switched off`() {
        val fixture = jvmFixture(dir)
        val classpath = arrayOf(":lib:dependencies", "--configuration", "testRuntimeClasspath")

        val junit4 = fixture.build(*classpath).output
        val off = fixture.build(*classpath, "-Plvm.kotlin.testDependencies=false").output
        fixture.file("lib/build.gradle.kts", JVM_MODULE_HEADER + "tasks.test { useJUnitPlatform() }")
        val junit5 = fixture.build(*classpath).output

        assertContains(junit4, "org.jetbrains.kotlin:kotlin-test-junit:")
        assertContains(junit5, "org.jetbrains.kotlin:kotlin-test-junit5:")
        assertFalse(off.contains("org.jetbrains.kotlin:kotlin-test"), off)
    }

    @Test
    fun `the report has the plugin, the values with their sources, the source sets and Kotlin`() {
        val fixture = jvmFixture(dir, "lvm { kotlin { explicitApi = true } }", settingsLvm = "catalog = false\njvm { target = 17 }")

        fixture.build(":lib:lvmReport", "-Plvm.kotlin.warningsAsErrors=true")

        @Suppress("UNCHECKED_CAST")
        val report = JsonSlurper().parse(dir.resolve("build/lvm/report/lib/project.json")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val values = report.getValue("values") as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val toolchain = report.getValue("toolchain") as Map<String, Any?>
        assertEquals(listOf("tech.annexflow.lvm.jvm"), report["plugins"])
        assertEquals(mapOf("value" to 17, "source" to "settings"), values["jvm.target"])
        assertEquals(mapOf("value" to true, "source" to "property"), values["kotlin.warningsAsErrors"])
        assertEquals(mapOf("value" to true, "source" to "module"), values["kotlin.explicitApi"])
        assertEquals(listOf("main", "test"), report["sourceSets"])
        assertEquals(setOf("gradle", "kotlin"), toolchain.keys)
    }
}
