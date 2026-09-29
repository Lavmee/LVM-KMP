package tech.annexflow.lvm.blueprint.common

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/** Root of the `lvm { }` block. Each blueprint plugin adds its sections to it. */
abstract class LvmExtension : ExtensionAware

val Project.lvm: LvmExtension
    get() = extensions.findByType(LvmExtension::class.java)
        ?: extensions.create("lvm", LvmExtension::class.java)

/** Returns the section named [name], creating it and calling [onCreate] the first time. Several plugins share sections. */
inline fun <reified T : Any> LvmExtension.section(name: String, onCreate: (T) -> Unit): T {
    extensions.findByType(T::class.java)?.let { return it }
    return extensions.create(name, T::class.java).also(onCreate)
}
