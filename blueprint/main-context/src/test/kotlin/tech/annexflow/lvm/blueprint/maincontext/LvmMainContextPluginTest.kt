package tech.annexflow.lvm.blueprint.maincontext

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.testing.GradleFixture
import tech.annexflow.lvm.blueprint.testing.LvmTestVersions
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.agpVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.kotlinVersion
import tech.annexflow.lvm.blueprint.testing.GradleFixture.Companion.settingsHeader

class LvmMainContextPluginTest {

    @TempDir
    lateinit var dir: File

    private val compiler = "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_COMPILER_ARTIFACT}:${LvmBuildInfo.VERSION}"
    private val annotation = "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_ARTIFACT}:${LvmBuildInfo.VERSION}"

    @Test
    fun `platforms follow settings, then the Gradle property, then the module`() {
        val fixture = fixture(settingsLvm = "catalog = false\nmainContext { platforms.add(\"ios\") }")

        assertEquals(setOf("IosArm64Main", "IosArm64Test"), fixture.compilationsWithCompiler())
        assertEquals(setOf("JvmMain", "JvmTest"), fixture.compilationsWithCompiler("-Plvm.mainContext.platforms=jvm"))

        fixture.file("lib/build.gradle.kts", module("lvm { mainContext { platforms.set(listOf(\"ios\", \"jvm\")) } }"))
        assertEquals(
            setOf("IosArm64Main", "IosArm64Test", "JvmMain", "JvmTest"),
            fixture.compilationsWithCompiler("-Plvm.mainContext.platforms=jvm"),
        )
    }

    @Test
    fun `test compilations follow settings, then the Gradle property, then the module`() {
        val fixture = fixture(settingsLvm = "catalog = false\nmainContext { testCompilations = false }")
        // A compilation of its own, such as integration tests have, is not main code either.
        val integrationTest = "kotlin { jvm { compilations.create(\"integrationTest\") } }"
        fixture.file("lib/build.gradle.kts", module(integrationTest))
        val main = setOf("IosArm64Main", "JvmMain")

        assertEquals(main, fixture.compilationsWithCompiler())
        assertEquals(
            main + setOf("IosArm64Test", "JvmTest", "JvmIntegrationTest"),
            fixture.compilationsWithCompiler("-Plvm.mainContext.testCompilations=true"),
        )

        fixture.file("lib/build.gradle.kts", module("$integrationTest\nlvm { mainContext { testCompilations = false } }"))
        assertEquals(main, fixture.compilationsWithCompiler("-Plvm.mainContext.testCompilations=true"))
    }

    @Test
    fun `calls to MainContext functions in tests move to the main dispatcher unless test compilations are off`() {
        val fixture = runningFixture()

        fixture.file("lib/src/test/kotlin/NavigatorTest.kt", navigatorTest(callInTestMoves = true))
        assertEquals(TaskOutcome.SUCCESS, fixture.build(":lib:test").task(":lib:test")?.outcome)

        fixture.file("lib/src/test/kotlin/NavigatorTest.kt", navigatorTest(callInTestMoves = false))
        val result = fixture.build(":lib:test", "-Plvm.mainContext.testCompilations=false")
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:test")?.outcome)
    }

    @Test
    fun `an unknown platform fails the build`() {
        val result = fixture().buildAndFail(":lib:printMainContext", "-Plvm.mainContext.platforms=jvm,web")

        assertContains(result.output, "'web'")
    }

    @Test
    fun `adds the annotation to commonMain unless switched off`() {
        val fixture = fixture()

        assertContains(fixture.build(":lib:printMainContext").output, "annotation=$annotation")
        assertContains(fixture.build(":lib:printMainContext", "-Plvm.mainContext.dependency=false").output, "annotation=\n")
    }

    @Test
    fun `an Android module without Kotlin Multiplatform gets the annotation and the compiler plugin`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = GradleFixture(dir).apply {
            file("settings.gradle.kts", settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
            file(
                "build.gradle.kts",
                """
                plugins {
                    id("com.android.library") version "$agpVersion" apply false
                    id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                    id("tech.annexflow.lvm.android.library") apply false
                    id("tech.annexflow.lvm.main-context") apply false
                }
                """,
            )
            file(
                "lib/build.gradle.kts",
                """
                plugins {
                    id("tech.annexflow.lvm.android.library")
                    id("tech.annexflow.lvm.main-context")
                }
                group = "com.example"
                """ + printTask("implementation"),
            )
            file("lib/src/main/AndroidManifest.xml", "<manifest />")
            writeLocalProperties()
        }

        assertContains(fixture.build(":lib:printMainContext").output, "annotation=$annotation")
        assertContains(fixture.compilationsWithCompiler(), "Debug")
        assertEquals(emptySet(), fixture.compilationsWithCompiler("-Plvm.mainContext.platforms=jvm"))
    }

    @Test
    fun `with test compilations off, Android modules run the compiler plugin on their main code only`() {
        assumeTrue(GradleFixture.androidSdkDir() != null, "Android SDK not found")
        val fixture = GradleFixture(dir).apply {
            file("settings.gradle.kts", settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":lib\", \":kmp\")")
            file(
                "build.gradle.kts",
                """
                plugins {
                    id("com.android.library") version "$agpVersion" apply false
                    id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                    id("tech.annexflow.lvm.android.library") apply false
                    id("tech.annexflow.lvm.multiplatform") apply false
                    id("tech.annexflow.lvm.main-context") apply false
                }
                """,
            )
            // AGP's built-in Kotlin, with unit tests, instrumented tests and test fixtures.
            file(
                "lib/build.gradle.kts",
                """
                plugins {
                    id("tech.annexflow.lvm.android.library")
                    id("tech.annexflow.lvm.main-context")
                }
                group = "com.example"
                lvm { library { skipEmptyAndroidTest = false } }
                android { testFixtures { enable = true } }
                """ + printTask("implementation"),
            )
            file("lib/src/main/AndroidManifest.xml", "<manifest />")
            // The Android target of Kotlin Multiplatform, with host tests.
            file(
                "kmp/build.gradle.kts",
                """
                plugins {
                    id("tech.annexflow.lvm.multiplatform")
                    id("tech.annexflow.lvm.main-context")
                }
                group = "com.example"
                lvm { targets { android() } }
                """ + printTask("commonMainImplementation"),
            )
            writeLocalProperties()
        }

        val all = fixture.compilationsWithCompiler(":kmp:printMainContext")
        assertTrue(
            all.containsAll(setOf("DebugUnitTest", "DebugAndroidTest", "DebugTestFixtures", "AndroidHostTest")),
            "$all",
        )
        assertEquals(
            setOf("Debug", "Release", "AndroidMain"),
            fixture.compilationsWithCompiler(":kmp:printMainContext", "-Plvm.mainContext.testCompilations=false"),
        )
    }

    @Test
    fun `a Kotlin JVM module gets the annotation and the compiler plugin`() {
        val fixture = GradleFixture(dir).apply {
            file("settings.gradle.kts", settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
            file(
                "build.gradle.kts",
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
                    id("tech.annexflow.lvm.main-context") apply false
                }
                """,
            )
            file(
                "lib/build.gradle.kts",
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    `java-test-fixtures`
                    id("tech.annexflow.lvm.main-context")
                }
                sourceSets { create("integrationTest") }
                """ + printTask("implementation"),
            )
        }

        assertContains(fixture.build(":lib:printMainContext").output, "annotation=$annotation")
        assertEquals(setOf("Main", "Test", "TestFixtures", "IntegrationTest"), fixture.compilationsWithCompiler())
        assertEquals(setOf("Main"), fixture.compilationsWithCompiler("-Plvm.mainContext.testCompilations=false"))
    }

    private fun fixture(settingsLvm: String = "catalog = false"): GradleFixture = GradleFixture(dir).apply {
        file("settings.gradle.kts", settingsHeader(settingsLvm) + "\nrootProject.name = \"fixture\"\ninclude(\":lib\")")
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.multiplatform") apply false
                id("tech.annexflow.lvm.main-context") apply false
            }
            """,
        )
        file("lib/build.gradle.kts", module())
    }

    /**
     * A Kotlin/JVM module `:lib` with `Navigator.push`, a `@MainContext` function, and `open`, main code that calls it
     * from a coroutine. The annotation and the compiler plugin come from their sources, as in LVM's own build.
     */
    private fun runningFixture(): GradleFixture = GradleFixture(dir).apply {
        val sources = File(GradleFixture.blueprintDir).parentFile.invariantSeparatorsPath
        file(
            "settings.gradle.kts",
            settingsHeader() + "\nrootProject.name = \"fixture\"\ninclude(\":lib\", \":annotation\", \":compiler\")",
        )
        file(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
                id("tech.annexflow.lvm.jvm") apply false
                id("tech.annexflow.lvm.main-context") apply false
            }
            """,
        )
        file(
            "annotation/build.gradle.kts",
            """
            plugins { id("org.jetbrains.kotlin.jvm") }
            kotlin { sourceSets.main { kotlin.srcDir("$sources/runtime/main-context/src") } }
            dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${LvmTestVersions.KOTLINX_COROUTINES}") }
            """,
        )
        file(
            "compiler/build.gradle.kts",
            """
            plugins { id("org.jetbrains.kotlin.jvm") }
            kotlin {
                sourceSets.main { kotlin.srcDir("$sources/compiler/main-context-compiler/src") }
                compilerOptions { optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi") }
            }
            sourceSets.main { resources.srcDir("$sources/compiler/main-context-compiler/src/main/resources") }
            dependencies { compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion") }
            """,
        )
        file(
            "lib/build.gradle.kts",
            """
            plugins {
                id("tech.annexflow.lvm.jvm")
                id("tech.annexflow.lvm.main-context")
            }
            dependencies {
                testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${LvmTestVersions.KOTLINX_COROUTINES}")
            }
            configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    substitute(module("${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_ARTIFACT}")).using(project(":annotation"))
                    substitute(module("${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_COMPILER_ARTIFACT}"))
                        .using(project(":compiler"))
                }
            }
            """,
        )
        file(
            "lib/src/main/kotlin/Navigator.kt",
            """
            import tech.annexflow.lvm.maincontext.MainContext

            class Navigator {
                @MainContext
                fun push() {}
            }

            suspend fun open(navigator: Navigator) {
                navigator.push()
            }
            """,
        )
    }

    /**
     * Tests of `:lib` without a main dispatcher, where a call that moves to it fails: the call in `open` moves, and the
     * call in the test moves if [callInTestMoves].
     */
    private fun navigatorTest(callInTestMoves: Boolean): String {
        val callInTest = if (callInTestMoves) "assertFailsWith<IllegalStateException> { push() }" else "push()"
        return """
            import kotlin.test.Test
            import kotlin.test.assertFailsWith
            import kotlinx.coroutines.test.runTest

            class NavigatorTest {
                @Test
                fun callInTest() = runTest {
                    Navigator().apply { $callInTest }
                }

                @Test
                fun callInMainCode() = runTest {
                    assertFailsWith<IllegalStateException> { open(Navigator()) }
                }
            }
        """
    }

    /** A module with JVM and iOS targets, [extra] lines and a task that prints what the plugin added. */
    private fun module(extra: String = ""): String = """
        plugins {
            id("tech.annexflow.lvm.multiplatform")
            id("tech.annexflow.lvm.main-context")
        }
        lvm {
            targets {
                jvm()
                ios { architectures.set(listOf("iosArm64")) }
            }
        }
        $extra
    """ + printTask("commonMainImplementation")

    /**
     * A task that prints the compiler plugin classpath of each compilation as `<compilation>=<dependencies>` and the
     * dependencies of [annotationConfiguration] as `annotation=<dependencies>`.
     */
    private fun printTask(annotationConfiguration: String): String = """
        val prefix = "kotlinCompilerPluginClasspath"
        val lines = provider {
            fun coordinates(configuration: Configuration) =
                configuration.dependencies.joinToString { listOfNotNull(it.group, it.name, it.version).joinToString(":") }
            val compilations = configurations.filter { it.name.startsWith(prefix) }
                .map { it.name.removePrefix(prefix) + "=" + coordinates(it) }
            compilations + ("annotation=" + coordinates(configurations.getByName("$annotationConfiguration")))
        }
        tasks.register("printMainContext") {
            val text = lines
            doLast { text.get().forEach(::println) }
        }
    """

    /** The compilations whose compiler plugin classpath has the main-context compiler plugin. */
    private fun GradleFixture.compilationsWithCompiler(vararg arguments: String): Set<String> =
        build(":lib:printMainContext", *arguments).output.lines()
            .filter { line -> compiler in line.substringAfter('=').split(", ") }
            .map { line -> line.substringBefore('=') }
            .toSet()
}
