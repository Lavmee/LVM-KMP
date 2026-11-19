package tech.annexflow.lvm.blueprint.jvm

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.lvmReport
import tech.annexflow.lvm.blueprint.common.lvmValues
import tech.annexflow.lvm.blueprint.common.reportPlugin
import tech.annexflow.lvm.blueprint.common.reportTool
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle
import tech.annexflow.lvm.blueprint.common.requireSupported

/**
 * A Kotlin/JVM module: the Kotlin JVM plugin with the Java version of `lvm { jvm { } }`, the compiler options of
 * `lvm { kotlin { } }`, kotlin-test for unit tests and the flat source layout.
 */
class LvmJvmPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        val kotlinVersion = project.getKotlinPluginVersion()
        project.requireSupported(
            tool = "Kotlin Gradle plugin",
            actual = kotlinVersion,
            minimum = LvmBuildInfo.MIN_KOTLIN,
            tested = LvmBuildInfo.TESTED_KOTLIN,
        )
        project.reportPlugin("tech.annexflow.lvm.jvm")
        project.reportTool("kotlin", kotlinVersion, LvmBuildInfo.MIN_KOTLIN, LvmBuildInfo.TESTED_KOTLIN)

        val jvm = project.lvmJvm()
        val kotlinSection = project.lvmKotlin()
        val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
        val sourceSets = project.provider { kotlin.sourceSets.names.sorted() }
        project.lvmReport { fields.put("sourceSets", sourceSets) }

        project.configureJavaVersion(jvm, kotlin)
        configureCompilerOptions(kotlin, kotlinSection, project.lvmValues.kotlin)
        project.configureTestDependencies(kotlinSection)
        project.configureSourceLayout(kotlin, kotlinSection)
    }
}
