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
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

class LvmQualityPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("io.gitlab.arturbosch.detekt")

        val values = LvmValues(project)
        val jvm = project.lvmJvm()
        val section = project.lvm.extensions.create("quality", QualitySection::class.java, project)
        val flatLayout = project.lvmKotlin().flatLayout
        section.source.convention(
            flatLayout.flatMap { flat ->
                val default = if (flat) LvmDefaults.QUALITY_SOURCE_FLAT else LvmDefaults.QUALITY_SOURCE
                values.list(LvmKeys.QUALITY_SOURCE, default)
            },
        )
        section.buildUponDefaultConfig.convention(
            values.boolean(LvmKeys.QUALITY_BUILD_UPON_DEFAULT_CONFIG, LvmDefaults.QUALITY_BUILD_UPON_DEFAULT_CONFIG),
        )
        section.composeRules.convention(values.boolean(LvmKeys.QUALITY_COMPOSE_RULES, LvmDefaults.QUALITY_COMPOSE_RULES))

        @Suppress("UnstableApiUsage")
        val rootDirectory = project.isolated.rootProject.projectDirectory
        val projectDirectory = project.layout.projectDirectory
        val configFiles = values.list(LvmKeys.QUALITY_CONFIG, LvmDefaults.QUALITY_CONFIG)
            .zip(section.config) { buildWide, module -> (buildWide + module).map(rootDirectory::file) }

        val flatLayoutConfig = project.tasks.register("lvmDetektFlatLayoutConfig", WriteDetektConfig::class.java) {
            description = "Writes the detekt config for the flat source layout."
            text.set(flatLayoutDetektConfig())
            destination.set(project.layout.buildDirectory.file("lvm/detekt-flat-layout.yml"))
        }
        val flatLayoutConfigFiles = flatLayout.zip(flatLayoutConfig.flatMap { it.destination }) { flat, file ->
            if (flat) listOf(file) else emptyList()
        }

        project.extensions.configure(DetektExtension::class.java) {
            // Later config files override earlier ones, so the build's own files override the flat layout's.
            config.setFrom(flatLayoutConfigFiles, configFiles)
            // detekt's default source, src/main/kotlin, doesn't exist in a KMP module. Entries are directory patterns.
            source.setFrom(
                section.source.map { directories ->
                    projectDirectory.asFileTree.matching { directories.forEach { include("$it/**") } }
                },
            )
        }

        val buildUponDefaultConfig = section.buildUponDefaultConfig
        val jvmTarget = jvm.target.map(Int::toString)
        project.tasks.withType(Detekt::class.java).configureEach {
            dependsOn(flatLayoutConfig)
            this.buildUponDefaultConfig = buildUponDefaultConfig.get()
            this.jvmTarget = jvmTarget.get()
        }
        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            dependsOn(flatLayoutConfig)
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
