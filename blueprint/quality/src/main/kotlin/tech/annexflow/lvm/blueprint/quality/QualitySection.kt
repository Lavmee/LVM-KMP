package tech.annexflow.lvm.blueprint.quality

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** `lvm { quality { } }` */
abstract class QualitySection @Inject constructor(private val project: Project) {

    /** detekt config files, relative to the root project directory. Appended to the build-wide `lvm.quality.config`. */
    abstract val config: ListProperty<String>

    /** Directories to analyze, relative to this module. Replaces the build-wide `lvm.quality.source`. */
    abstract val source: ListProperty<String>

    abstract val buildUponDefaultConfig: Property<Boolean>

    /** Adds `ru.kode:detekt-rules-compose`. */
    abstract val composeRules: Property<Boolean>

    /** Adds a detekt rule set, for example `rules(project(":detekt-rules"))`. */
    fun rules(notation: Any) {
        project.dependencies.add("detektPlugins", notation)
    }
}
