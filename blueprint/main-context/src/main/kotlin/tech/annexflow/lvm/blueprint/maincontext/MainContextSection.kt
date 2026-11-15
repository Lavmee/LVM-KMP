package tech.annexflow.lvm.blueprint.maincontext

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import tech.annexflow.lvm.blueprint.common.LvmExtension
import tech.annexflow.lvm.blueprint.common.requireSection

/** `lvm { mainContext { } }` */
abstract class MainContextSection {
    /**
     * Platforms whose compilations, main and test, run the compiler plugin: any of `android`, `ios`, `jvm`. An empty
     * list turns the plugin off. Replaces the build-wide `lvm.mainContext.platforms`.
     */
    abstract val platforms: ListProperty<String>

    /** Adds `lvm-main-context`, with the `@MainContext` annotation, to `commonMain`. */
    abstract val dependency: Property<Boolean>
}

/** The module's `lvm { mainContext { } }`, which `tech.annexflow.lvm.main-context` adds. */
val LvmExtension.mainContext: MainContextSection
    get() = requireSection(MainContextSection::class.java, "mainContext", "tech.annexflow.lvm.main-context")

fun LvmExtension.mainContext(action: Action<MainContextSection>) = action.execute(mainContext)
