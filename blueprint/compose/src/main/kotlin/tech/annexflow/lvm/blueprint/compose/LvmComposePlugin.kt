package tech.annexflow.lvm.blueprint.compose

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/** Compose Multiplatform and the Compose compiler for a Kotlin Multiplatform module. */
class LvmComposePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        val values = LvmValues(project)
        val section = project.lvm.extensions.create("compose", ComposeSection::class.java)
        section.metrics.convention(values.boolean(LvmKeys.COMPOSE_METRICS, LvmDefaults.COMPOSE_METRICS))
        section.reports.convention(values.boolean(LvmKeys.COMPOSE_REPORTS, LvmDefaults.COMPOSE_REPORTS))
        section.metricsDirectory.convention(project.layout.buildDirectory.dir("compose-metrics"))
        section.reportsDirectory.convention(project.layout.buildDirectory.dir("compose-reports"))

        project.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        project.pluginManager.apply("org.jetbrains.compose")
        configureCompiler(project, values, section)
    }

    private fun configureCompiler(project: Project, values: LvmValues, section: ComposeSection) {
        @Suppress("UnstableApiUsage")
        val rootDirectory = project.isolated.rootProject.projectDirectory
        val stabilityFiles = values
            .list(LvmKeys.COMPOSE_STABILITY_CONFIGURATION_FILES, LvmDefaults.COMPOSE_STABILITY_CONFIGURATION_FILES)
            .zip(section.stabilityConfigurationFiles) { buildWide, module -> (buildWide + module).map(rootDirectory::file) }
        // Unset, so the compiler writes nothing while a switch is off.
        val off = project.objects.directoryProperty()

        project.extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            metricsDestination.set(section.metrics.flatMap { on -> if (on) section.metricsDirectory else off })
            reportsDestination.set(section.reports.flatMap { on -> if (on) section.reportsDirectory else off })
            stabilityConfigurationFiles.addAll(stabilityFiles)
        }
    }
}
