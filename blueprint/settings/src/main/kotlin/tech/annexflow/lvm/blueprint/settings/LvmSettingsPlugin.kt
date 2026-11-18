package tech.annexflow.lvm.blueprint.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import tech.annexflow.lvm.blueprint.common.LVM_SETTINGS_PREFIX
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.requireSupportedGradle

class LvmSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        // The only place that warns about a Gradle newer than tested: once per build, not once per module.
        requireSupportedGradle(Logging.getLogger(LvmSettingsPlugin::class.java))

        val extension = settings.extensions.create("lvm", LvmSettingsExtension::class.java)
        extension.catalog.convention(true)
        extension.catalogName.convention("lvmLibs")
        extension.catalogVersion.convention(LvmBuildInfo.VERSION)
        extension.repositories.convention(true)

        // The settings script configures `lvm { }` after this plugin is applied, so act once it has been evaluated.
        settings.gradle.settingsEvaluated {
            if (extension.repositories.get()) {
                dependencyResolutionManagement.repositories.google()
                dependencyResolutionManagement.repositories.mavenCentral()
            }
            if (extension.catalog.get()) {
                val notation = "tech.annexflow.lvm:lvm-versions:${extension.catalogVersion.get()}"
                dependencyResolutionManagement.versionCatalogs.create(extension.catalogName.get()) { from(notation) }
            }
            @Suppress("UnstableApiUsage")
            gradle.lifecycle.beforeProject(ForwardLvmValues(extension.forwardedValues() + extension.reportedOptions()))
        }
    }
}

/**
 * The plugin's own options as `lvm.settings.<name>` extra properties, which `lvmReport` writes. They pass as strings,
 * since project plugins may see blueprint's classes through another class loader.
 */
private fun LvmSettingsExtension.reportedOptions(): Map<String, String> = mapOf(
    LVM_SETTINGS_PREFIX + "catalog" to catalog.get().toString(),
    LVM_SETTINGS_PREFIX + "catalogName" to catalogName.get(),
    LVM_SETTINGS_PREFIX + "catalogVersion" to catalogVersion.get(),
    LVM_SETTINGS_PREFIX + "repositories" to repositories.get().toString(),
)
