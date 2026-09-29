package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmCompatibility
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.requireSupported

class LvmMultiplatformPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.requireSupported(
            tool = "Kotlin Gradle plugin",
            actual = project.getKotlinPluginVersion(),
            minimum = LvmCompatibility.MIN_KOTLIN,
            tested = LvmBuildInfo.TESTED_KOTLIN,
        )

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val values = LvmValues(project)
        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlinSection = project.lvmKotlin()

        project.lvm.extensions.create(
            "targets",
            TargetsSection::class.java,
            project.objects,
            KmpTargetWiring(project, kotlin, android, jvm, values),
        )

        configureKotlinCompilerOptions(kotlin, kotlinSection, values)
        configureJvmTarget(kotlin, jvm)
        project.configureTestDependencies(kotlinSection)
    }
}
