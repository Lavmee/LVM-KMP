package tech.annexflow.lvm.blueprint.android

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** `lvm { library { } }` */
abstract class LibrarySection {
    /** Core library desugaring, with `desugar_jdk_libs`. */
    abstract val desugaring: Property<Boolean>

    /** Keep rules that ship in the AAR, relative to the module; files that don't exist are skipped. Appended to the build-wide list. */
    abstract val consumerProguardFiles: ListProperty<String>

    /** Sets `resourcePrefix` to one derived from the module path: `:core:ui` -> `core_ui_`. */
    abstract val resourcePrefix: Property<Boolean>

    /** Turns off the instrumented tests of a module without `androidTest` sources. */
    abstract val skipEmptyAndroidTest: Property<Boolean>
}
