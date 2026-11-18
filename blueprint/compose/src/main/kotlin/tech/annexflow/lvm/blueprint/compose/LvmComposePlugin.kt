package tech.annexflow.lvm.blueprint.compose

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmReport
import tech.annexflow.lvm.blueprint.common.reportPlugin
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/**
 * The Compose compiler for a module with Compose code, and Compose Multiplatform for a Kotlin Multiplatform module. An
 * Android module without Kotlin Multiplatform gets only the compiler, so its build doesn't need Compose Multiplatform.
 */
class LvmComposePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        val values = LvmValues(project)
        val section = project.lvm.extensions.create("compose", ComposeSection::class.java)
        val metrics = values.boolean(LvmKeys.COMPOSE_METRICS, LvmDefaults.COMPOSE_METRICS)
        val reports = values.boolean(LvmKeys.COMPOSE_REPORTS, LvmDefaults.COMPOSE_REPORTS)
        section.metrics.convention(metrics)
        section.reports.convention(reports)
        section.metricsDirectory.convention(project.layout.buildDirectory.dir("compose-metrics"))
        section.reportsDirectory.convention(project.layout.buildDirectory.dir("compose-reports"))
        project.reportPlugin("tech.annexflow.lvm.compose")
        values.report(LvmKeys.COMPOSE_METRICS, section.metrics, metrics)
        values.report(LvmKeys.COMPOSE_REPORTS, section.reports, reports)
        reportDirectories(project, section)

        project.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        // Compose Multiplatform adds nothing to an Android module without Kotlin Multiplatform, and applying it there
        // would require it on the build classpath. withPlugin also fires when Kotlin Multiplatform comes later.
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.pluginManager.apply("org.jetbrains.compose")
        }
        configureCompiler(project, values, section)
    }

    private fun configureCompiler(project: Project, values: LvmValues, section: ComposeSection) {
        @Suppress("UnstableApiUsage")
        val rootDirectory = project.isolated.rootProject.projectDirectory
        val buildWideStabilityFiles =
            values.list(LvmKeys.COMPOSE_STABILITY_CONFIGURATION_FILES, LvmDefaults.COMPOSE_STABILITY_CONFIGURATION_FILES)
        val stabilityFileNames = buildWideStabilityFiles
            .zip(section.stabilityConfigurationFiles) { buildWide, module -> buildWide + module }
        values.report(LvmKeys.COMPOSE_STABILITY_CONFIGURATION_FILES, stabilityFileNames, buildWideStabilityFiles)
        val stabilityFiles = stabilityFileNames.map { names -> names.map(rootDirectory::file) }
        // Unset, so the compiler writes nothing while a switch is off.
        val off = project.objects.directoryProperty()

        project.extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            metricsDestination.set(section.metrics.flatMap { on -> if (on) section.metricsDirectory else off })
            reportsDestination.set(section.reports.flatMap { on -> if (on) section.reportsDirectory else off })
            stabilityConfigurationFiles.addAll(stabilityFiles)
        }
    }
}

/** Adds where the compiler writes its metrics and reports, relative to the module, to the module's report. */
private fun reportDirectories(project: Project, section: ComposeSection) {
    val moduleDirectory = project.layout.projectDirectory.asFile
    val directories = section.metricsDirectory.zip(section.reportsDirectory) { metrics, reports ->
        linkedMapOf(
            "composeMetrics" to metrics.asFile.relativeToOrSelf(moduleDirectory).invariantSeparatorsPath,
            "composeReports" to reports.asFile.relativeToOrSelf(moduleDirectory).invariantSeparatorsPath,
        )
    }
    project.lvmReport { fields.put("reports", directories) }
}
