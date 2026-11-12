package tech.annexflow.lvm.blueprint.compose

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** `lvm { compose { } }` */
abstract class ComposeSection {
    /** Writes the Compose compiler's metrics into [metricsDirectory]. */
    abstract val metrics: Property<Boolean>

    /** Where metrics go. Defaults to `build/compose-metrics` of the module. */
    abstract val metricsDirectory: DirectoryProperty

    /** Writes the Compose compiler's reports, such as the stability of classes, into [reportsDirectory]. */
    abstract val reports: Property<Boolean>

    /** Where reports go. Defaults to `build/compose-reports` of the module. */
    abstract val reportsDirectory: DirectoryProperty

    /**
     * Stability configuration files of the Compose compiler, relative to the root project directory. Appended to the
     * build-wide `lvm.compose.stabilityConfigurationFiles`.
     */
    abstract val stabilityConfigurationFiles: ListProperty<String>
}
