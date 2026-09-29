package tech.annexflow.lvm.blueprint.settings

import org.gradle.api.IsolatedAction
import org.gradle.api.Project

/** Sets build-wide values as `lvm.*` extra properties on a project before its build script runs. Holds only strings. */
internal class ForwardLvmValues(private val values: Map<String, String>) : IsolatedAction<Project> {
    override fun execute(project: Project) {
        values.forEach { (key, value) -> project.extensions.extraProperties.set(key, value) }
    }
}
