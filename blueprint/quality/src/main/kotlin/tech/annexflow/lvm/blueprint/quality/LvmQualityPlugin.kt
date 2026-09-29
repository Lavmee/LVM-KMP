package tech.annexflow.lvm.blueprint.quality

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

class LvmQualityPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("io.gitlab.arturbosch.detekt")

        val values = LvmValues(project)
        val jvm = project.lvmJvm()
        val section = project.lvm.extensions.create("quality", QualitySection::class.java, project)
        section.source.convention(values.list(LvmKeys.QUALITY_SOURCE, LvmDefaults.QUALITY_SOURCE))
        section.buildUponDefaultConfig.convention(
            values.boolean(LvmKeys.QUALITY_BUILD_UPON_DEFAULT_CONFIG, LvmDefaults.QUALITY_BUILD_UPON_DEFAULT_CONFIG),
        )
        section.composeRules.convention(values.boolean(LvmKeys.QUALITY_COMPOSE_RULES, LvmDefaults.QUALITY_COMPOSE_RULES))

        @Suppress("UnstableApiUsage")
        val rootDirectory = project.isolated.rootProject.projectDirectory
        val projectDirectory = project.layout.projectDirectory
        val configFiles = values.list(LvmKeys.QUALITY_CONFIG, LvmDefaults.QUALITY_CONFIG)
            .zip(section.config) { buildWide, module -> (buildWide + module).map(rootDirectory::file) }

        project.extensions.configure(DetektExtension::class.java) {
            config.setFrom(configFiles)
            // detekt's default source is src/main/kotlin, which a KMP module doesn't have.
            source.setFrom(section.source.map { directories -> directories.map(projectDirectory::dir) })
        }

        val buildUponDefaultConfig = section.buildUponDefaultConfig
        val jvmTarget = jvm.target.map(Int::toString)
        project.tasks.withType(Detekt::class.java).configureEach {
            this.buildUponDefaultConfig = buildUponDefaultConfig.get()
            this.jvmTarget = jvmTarget.get()
        }
        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            this.buildUponDefaultConfig.set(buildUponDefaultConfig)
            this.jvmTarget = jvmTarget.get()
        }

        val composeRules = project.dependencies.create(LvmBuildInfo.DETEKT_COMPOSE_RULES)
        val composeRulesEnabled = section.composeRules
        project.configurations.named("detektPlugins").configure {
            withDependencies { if (composeRulesEnabled.get()) add(composeRules) }
        }

        // detekt runs on the Kotlin version it was built with; stop KGP from aligning its configuration to the project's Kotlin.
        project.configurations.matching { it.name == "detekt" }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") useVersion(getSupportedKotlinVersion())
            }
        }
    }
}
