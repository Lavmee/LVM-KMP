package tech.annexflow.lvm.blueprint.android

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import tech.annexflow.lvm.blueprint.common.LvmExtension
import tech.annexflow.lvm.blueprint.common.requireSection

/** `lvm { library { } }` */
abstract class LibrarySection {
    /** Core library desugaring, with `desugar_jdk_libs`. */
    abstract val desugaring: Property<Boolean>

    /** Keep rules that ship in the AAR, relative to the module; files that don't exist are skipped. Appended to the build-wide list. */
    abstract val consumerProguardFiles: ListProperty<String>

    /** Sets `resourcePrefix` to one derived from the module path: `:core:ui` -> `core_ui_`. */
    abstract val resourcePrefix: Property<Boolean>

    /**
     * Segments that the resource prefix leaves out at the end of the module path, compared without case, while more
     * than one segment is left. A segment is a run of letters, digits and `_`: with `ui` and `sky`,
     * `:feature:chat:ui-sky` -> `feature_chat_`. Appended to the build-wide list.
     */
    abstract val resourcePrefixDroppedSegments: ListProperty<String>

    /** Turns off the instrumented tests of a module without `androidTest` sources. */
    abstract val skipEmptyAndroidTest: Property<Boolean>
}

/** The module's `lvm { library { } }`, which `tech.annexflow.lvm.android.library` adds. */
val LvmExtension.library: LibrarySection
    get() = requireSection(LibrarySection::class.java, "library", "tech.annexflow.lvm.android.library")

fun LvmExtension.library(action: Action<LibrarySection>) = action.execute(library)
