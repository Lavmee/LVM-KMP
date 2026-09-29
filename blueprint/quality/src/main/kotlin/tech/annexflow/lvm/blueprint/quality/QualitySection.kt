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

    /**
     * Adds a detekt rule set: a `Project`, as in `rules(project(":detekt-rules"))`, or any other dependency notation,
     * such as `"com.example:detekt-rules:1.0"` or a catalog accessor.
     */
    fun rules(notation: Any) {
        // Inside lvm { }, project(":x") returns the Project itself, and Gradle deprecates a Project as dependency notation.
        val dependency = if (notation is Project) project.dependencyFactory.createProjectDependency(notation.path) else notation
        project.dependencies.add("detektPlugins", dependency)
    }
}
