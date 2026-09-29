package tech.annexflow.lvm.blueprint.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo

class LvmSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
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
            gradle.lifecycle.beforeProject(ForwardLvmValues(extension.forwardedValues()))
        }
    }
}
