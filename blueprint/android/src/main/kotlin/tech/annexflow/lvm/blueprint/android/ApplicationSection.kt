package tech.annexflow.lvm.blueprint.android

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import tech.annexflow.lvm.blueprint.common.LvmExtension
import tech.annexflow.lvm.blueprint.common.requireSection

/** `lvm { application { } }` */
abstract class ApplicationSection {
    /** Default: unset, so AGP uses the namespace. */
    abstract val applicationId: Property<String>
    abstract val versionCode: Property<Int>
    abstract val versionName: Property<String>

    /** Turns on R8 and resource shrinking for the release build type. */
    abstract val minify: Property<Boolean>

    /** Keep rules of the release build type, relative to the module. Appended to the build-wide list. */
    abstract val proguardFiles: ListProperty<String>

    /** Core library desugaring, with `desugar_jdk_libs`. */
    abstract val desugaring: Property<Boolean>
}

/** The module's `lvm { application { } }`, which `tech.annexflow.lvm.android.application` adds. */
val LvmExtension.application: ApplicationSection
    get() = requireSection(ApplicationSection::class.java, "application", "tech.annexflow.lvm.android.application")

fun LvmExtension.application(action: Action<ApplicationSection>) = action.execute(application)
