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

    /** AGP reads the SDK location from here when ANDROID_HOME is not set for the test process. */
    fun writeLocalProperties() {
        val sdk = checkNotNull(androidSdkDir()) { "Android SDK not found: set ANDROID_HOME." }
        file("local.properties", "sdk.dir=${sdk.absolutePath.replace('\\', '/')}")
    }

    private fun runner(arguments: Array<out String>): GradleRunner =
        GradleRunner.create()
            .withProjectDir(rootDir)
            .withArguments(listOf("--configuration-cache", "--stacktrace") + arguments)
            .forwardOutput()

    companion object {
        val blueprintDir: String = requireNotNull(System.getProperty("lvm.blueprint.dir")) {
            "System property lvm.blueprint.dir is not set: apply lvm.blueprint-fixture-tests to the module."
        }.replace('\\', '/')
        val kotlinVersion: String = LvmBuildInfo.TESTED_KOTLIN
        val agpVersion: String = LvmBuildInfo.TESTED_AGP
        val detektVersion: String = LvmTestVersions.DETEKT

        /** Start of settings.gradle.kts: blueprint from sources, the settings plugin, and [lvmBlock] inside `lvm { }`. */
        fun settingsHeader(lvmBlock: String = "catalog = false"): String = """
            |pluginManagement {
            |    includeBuild("$blueprintDir")
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
    }
}
