package tech.annexflow.lvm.blueprint.testing

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo

/** A throwaway Gradle build that resolves blueprint plugins from the blueprint sources, like a real consumer would. */
class GradleFixture(val rootDir: File) {

    fun file(path: String, content: String): File =
        rootDir.resolve(path).apply {
            parentFile.mkdirs()
            writeText(content.trimIndent() + "\n")
        }

    fun build(vararg arguments: String): BuildResult = runner(arguments).build()

    fun buildAndFail(vararg arguments: String): BuildResult = runner(arguments).buildAndFail()

    /**
     * Writes `build-logic`, an included build with the plugin `consumer`, implemented by the class `ConsumerPlugin` in
     * [source]. It compiles against the classes of [api] with `compileOnly`, as a consumer's build logic compiles against
     * blueprint, so at runtime the classes come from the fixture's blueprint plugins. [settingsHeader] includes it when
     * called with `buildLogic = true`.
     */
    fun buildLogic(source: String, vararg api: Class<*>) {
        val classpath = api.map { File(it.protectionDomain.codeSource.location.toURI()).invariantSeparatorsPath }.distinct()
        file(
            "build-logic/settings.gradle.kts",
            """
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "build-logic"
            """,
        )
        file(
            "build-logic/build.gradle.kts",
            """
            plugins { `kotlin-dsl` }
            dependencies { compileOnly(files(${classpath.joinToString { "\"$it\"" }})) }
            gradlePlugin {
                plugins {
                    register("consumer") {
                        id = "consumer"
                        implementationClass = "ConsumerPlugin"
                    }
                }
            }
            """,
        )
        file("build-logic/src/main/kotlin/ConsumerPlugin.kt", source)
    }

    /** AGP reads the SDK location from here when ANDROID_HOME is not set for the test process. */
    fun writeLocalProperties() {
        val sdk = checkNotNull(androidSdkDir()) { "Android SDK not found: set ANDROID_HOME." }
        file("local.properties", "sdk.dir=${sdk.absolutePath.replace('\\', '/')}")
    }

    private fun runner(arguments: Array<out String>): GradleRunner =
        GradleRunner.create()
            .withProjectDir(rootDir)
            .withTestKitDir(testKitDir)
            .withArguments(listOf("--configuration-cache", "--stacktrace") + arguments)
            .forwardOutput()

    companion object {
        val blueprintDir: String = fixtureProperty("lvm.blueprint.dir").replace('\\', '/')
        val kotlinVersion: String = LvmBuildInfo.TESTED_KOTLIN
        val agpVersion: String = LvmBuildInfo.TESTED_AGP
        val detektVersion: String = LvmTestVersions.DETEKT

        /** The Gradle user home that the fixture builds of every blueprint module share. */
        private val testKitDir: File = File(fixtureProperty("lvm.blueprint.testKitDir"))

        /**
         * Start of settings.gradle.kts: blueprint from sources, the settings plugin, and [lvmBlock] inside `lvm { }`.
         * With [buildLogic], it also includes the build that [GradleFixture.buildLogic] writes.
         */
        fun settingsHeader(lvmBlock: String = "catalog = false", buildLogic: Boolean = false): String = """
            |pluginManagement {
            |    includeBuild("$blueprintDir")${if (buildLogic) "\n|    includeBuild(\"build-logic\")" else ""}
            |    repositories {
            |        google()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |plugins {
            |    id("tech.annexflow.lvm.settings")
            |}
            |lvm {
            |$lvmBlock
            |}
            |""".trimMargin()

        fun androidSdkDir(): File? =
            sequenceOf(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                File(System.getProperty("user.home"), "Library/Android/sdk").path,
            ).filterNotNull().map(::File).firstOrNull(File::isDirectory)

        private fun fixtureProperty(name: String): String = requireNotNull(System.getProperty(name)) {
            "System property $name is not set: apply lvm.blueprint-fixture-tests to the module."
        }
    }
}
