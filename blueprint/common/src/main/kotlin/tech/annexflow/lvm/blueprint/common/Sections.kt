package tech.annexflow.lvm.blueprint.common

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** `lvm { android { } }` */
abstract class AndroidSection {
    abstract val compileSdk: Property<Int>

    /**
     * Minor level of [compileSdk]: 1 with a compileSdk of 37 compiles against 37.1. Unset by default, so the module
     * compiles against [compileSdk] itself. A minor level belongs to one SDK level, so a module whose [compileSdk]
     * differs from the build-wide one doesn't take the build-wide minor level.
     */
    abstract val compileSdkMinor: Property<Int>

    abstract val minSdk: Property<Int>

    /** Target SDK level of an application or a test module, and of the instrumented tests and lint of an Android library. */
    abstract val targetSdk: Property<Int>

    /** Default: the project group followed by the module path, with `-` replaced by `_`. */
    abstract val namespace: Property<String>

    /** Runner of instrumented tests in applications, Android libraries and test modules. */
    abstract val testInstrumentationRunner: Property<String>
}

/** The module's `lvm { android { } }`. It exists once a plugin that uses the section is applied. */
val LvmExtension.android: AndroidSection
    get() = requireSection(AndroidSection::class.java, "android", ANDROID_PLUGINS)

fun LvmExtension.android(action: Action<AndroidSection>) = action.execute(android)

/** `lvm { jvm { } }` */
abstract class JvmSection {
    /** Bytecode version of JVM and Android compilations, for example 17 or 21. */
    abstract val target: Property<Int>
}

/** The module's `lvm { jvm { } }`. It exists once a plugin that uses the section is applied. */
val LvmExtension.jvm: JvmSection
    get() = requireSection(JvmSection::class.java, "jvm", JVM_AND_KOTLIN_PLUGINS)

fun LvmExtension.jvm(action: Action<JvmSection>) = action.execute(jvm)

/** `lvm { kotlin { } }` */
abstract class KotlinSection {
    abstract val warningsAsErrors: Property<Boolean>

    /** Appended to the build-wide `lvm.kotlin.optIns`. */
    abstract val optIns: ListProperty<String>

    /** Appended to the build-wide `lvm.kotlin.freeCompilerArgs`. */
    abstract val freeCompilerArgs: ListProperty<String>

    /** Strict explicit API mode for main compilations. */
    abstract val explicitApi: Property<Boolean>

    /** Adds `kotlin-test` to `commonTest`. */
    abstract val testDependencies: Property<Boolean>

    /**
     * Sources in `src`, `test`, `src@<name>` and `test@<name>` instead of `src/<sourceSet>/kotlin`, with the module's
     * root package left out of the directories.
     */
    abstract val flatLayout: Property<Boolean>

    /**
     * How Kotlin compiles interface functions with a body for JVM and Android: `enable`, `no-compatibility` or
     * `disable`, as Kotlin's `-jvm-default`. Unset by default, so Kotlin's own default applies.
     */
    abstract val jvmDefault: Property<String>
}

/** The module's `lvm { kotlin { } }`. It exists once a plugin that uses the section is applied. */
val LvmExtension.kotlin: KotlinSection
    get() = requireSection(KotlinSection::class.java, "kotlin", JVM_AND_KOTLIN_PLUGINS)

fun LvmExtension.kotlin(action: Action<KotlinSection>) = action.execute(kotlin)

private const val ANDROID_PLUGINS = "tech.annexflow.lvm.multiplatform, tech.annexflow.lvm.android.application, " +
    "tech.annexflow.lvm.android.library or tech.annexflow.lvm.android.test"
private const val JVM_AND_KOTLIN_PLUGINS = "tech.annexflow.lvm.multiplatform, tech.annexflow.lvm.jvm, " +
    "tech.annexflow.lvm.android.application, tech.annexflow.lvm.android.library, tech.annexflow.lvm.android.test " +
    "or tech.annexflow.lvm.quality"
