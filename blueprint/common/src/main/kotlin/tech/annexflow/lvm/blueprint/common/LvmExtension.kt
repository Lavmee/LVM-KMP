package tech.annexflow.lvm.blueprint.common

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/** Root of the `lvm { }` block. Each blueprint plugin adds its sections to it. */
abstract class LvmExtension : ExtensionAware

val Project.lvm: LvmExtension
    get() = extensions.findByType(LvmExtension::class.java)
        ?: extensions.create("lvm", LvmExtension::class.java)

/** Returns the section named [name], creating it and calling [onCreate] the first time. Several plugins share sections. */
@InternalBlueprintApi
inline fun <reified T : Any> LvmExtension.section(name: String, onCreate: (T) -> Unit): T {
    extensions.findByType(T::class.java)?.let { return it }
    return extensions.create(name, T::class.java).also(onCreate)
}

/** Returns the section of type [type] for an accessor of `lvm { }`, failing with the [plugins] that add it when none did. */
@InternalBlueprintApi
fun <T : Any> LvmExtension.requireSection(type: Class<T>, name: String, plugins: String): T =
    extensions.findByType(type) ?: throw InvalidUserCodeException(
        "lvm { $name { } } exists only in a module with $plugins: apply it first, or reach the section inside " +
            "pluginManager.withPlugin(...) when your plugin may be applied before it.",
    )
