package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.lvmReport
import tech.annexflow.lvm.blueprint.common.lvmValues
import tech.annexflow.lvm.blueprint.common.reportPlugin
import tech.annexflow.lvm.blueprint.common.reportTool
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle
import tech.annexflow.lvm.blueprint.common.requireSupported

class LvmMultiplatformPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlinVersion = project.getKotlinPluginVersion()
        project.requireSupported(
            tool = "Kotlin Gradle plugin",
            actual = kotlinVersion,
            minimum = LvmBuildInfo.MIN_KOTLIN,
            tested = LvmBuildInfo.TESTED_KOTLIN,
        )
        project.reportPlugin("tech.annexflow.lvm.multiplatform")
        project.reportTool("kotlin", kotlinVersion, LvmBuildInfo.MIN_KOTLIN, LvmBuildInfo.TESTED_KOTLIN)

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val sourceSets = project.provider { kotlin.sourceSets.names.sorted() }
        project.lvmReport { fields.put("sourceSets", sourceSets) }
        val values = LvmValues(project)
        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlinSection = project.lvmKotlin()

        project.lvm.extensions.create(
            "targets",
            TargetsSection::class.java,
            project.objects,
            KmpTargetWiring(project, kotlin, android, jvm, kotlinSection, values),
        )

        configureKotlinCompilerOptions(kotlin, kotlinSection, project.lvmValues.kotlin)
        configureJvmTarget(kotlin, jvm, kotlinSection)
        project.configureTestDependencies(kotlinSection)
        project.configureSourceLayout(kotlin, kotlinSection)
    }
}
