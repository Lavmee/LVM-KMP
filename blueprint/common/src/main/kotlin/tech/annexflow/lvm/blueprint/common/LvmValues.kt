package tech.annexflow.lvm.blueprint.common

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Build-wide values of one project: the `lvm.<key>` Gradle property, then the value forwarded from settings, then the default.
 * A module's own `lvm { }` block sits above these; sections apply the result as a convention.
 */
@InternalBlueprintApi
class LvmValues(private val project: Project) {

    // The settings plugin sets these extra properties before the build script runs, so reading them once here is enough.
    private val fromSettings: Map<String, String> = project.extensions.extraProperties.properties
        .filterKeys { it.startsWith(LvmKeys.PREFIX) }
        .mapValues { (_, value) -> value.toString() }

    fun raw(key: String): Provider<String> {
        val gradleProperty = project.providers.gradleProperty(LvmKeys.property(key))
        val settingsValue = fromSettings[LvmKeys.property(key)] ?: return gradleProperty
        return gradleProperty.orElse(settingsValue)
    }

    fun int(key: String, default: Int): Provider<Int> =
        raw(key).map { LvmValueParsing.int(key, it) }.orElse(default)

    fun boolean(key: String, default: Boolean): Provider<Boolean> =
        raw(key).map { LvmValueParsing.boolean(key, it) }.orElse(default)

    fun list(key: String, default: List<String>): Provider<List<String>> =
        raw(key).map(LvmValueParsing::list).orElse(default)

    /** Where the build-wide value of [key] comes from: `property`, `settings` or `default`. */
    fun source(key: String): Provider<String> {
        val withoutProperty = if (LvmKeys.property(key) in fromSettings) "settings" else "default"
        return project.providers.gradleProperty(LvmKeys.property(key)).map { "property" }.orElse(withoutProperty)
    }

    /**
     * Adds the value of [key] to the module's `lvmReport`: [effective] is the value the plugins use, and [buildWide] the
     * one the module gets without its own `lvm { }`.
     */
    fun <T : Any> report(key: String, effective: Provider<T>, buildWide: Provider<T>) {
        project.reportValue(key, effective, buildWide, source(key))
    }
}
