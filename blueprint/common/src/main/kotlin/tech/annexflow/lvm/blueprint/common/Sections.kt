package tech.annexflow.lvm.blueprint.common

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/** `lvm { android { } }` */
abstract class AndroidSection {
    abstract val compileSdk: Property<Int>
    abstract val minSdk: Property<Int>

    /** Default: the project group followed by the module path, with `-` replaced by `_`. */
    abstract val namespace: Property<String>
}

/** `lvm { jvm { } }` */
abstract class JvmSection {
    /** Bytecode version of JVM and Android compilations, for example 17 or 21. */
    abstract val target: Property<Int>
}

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
}
