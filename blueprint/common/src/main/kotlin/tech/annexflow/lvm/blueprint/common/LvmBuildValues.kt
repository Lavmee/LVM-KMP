package tech.annexflow.lvm.blueprint.common

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * LVM's build-wide values for this project, as LVM's plugins resolve them: the `lvm.<section>.<name>` Gradle property,
 * then the value set in `lvm { }` of `settings.gradle.kts`, then LVM's default. A module's own `lvm { }` block is not
 * part of them, so every module of the build reads the same values.
 *
 * The values are lazy and can be read at any time during configuration. A value that can't be parsed, such as
 * `lvm.android.minSdk=abc`, fails when it is read.
 */
val Project.lvmValues: LvmBuildValues
    get() = LvmBuildValues(LvmValues(this))

/** Build-wide values by section of `lvm { }`. */
class LvmBuildValues internal constructor(values: LvmValues) {
    val android: AndroidValues = AndroidValues(values)
    val jvm: JvmValues = JvmValues(values)
    val kotlin: KotlinValues = KotlinValues(values)
}

/** Build-wide values of `lvm { android { } }`. */
class AndroidValues internal constructor(values: LvmValues) {
    val compileSdk: Provider<Int> = values.int(LvmKeys.ANDROID_COMPILE_SDK, LvmDefaults.ANDROID_COMPILE_SDK)
    val minSdk: Provider<Int> = values.int(LvmKeys.ANDROID_MIN_SDK, LvmDefaults.ANDROID_MIN_SDK)

    /** Target SDK level of applications and test modules, and of the instrumented tests and lint of Android libraries. */
    val targetSdk: Provider<Int> = values.int(LvmKeys.ANDROID_TARGET_SDK, LvmDefaults.ANDROID_TARGET_SDK)

    /** Runner of instrumented tests in applications, Android libraries and test modules. */
    val testInstrumentationRunner: Provider<String> = values.raw(LvmKeys.ANDROID_TEST_INSTRUMENTATION_RUNNER)
        .orElse(LvmDefaults.ANDROID_TEST_INSTRUMENTATION_RUNNER)

    /** Whether the Android target of a Kotlin Multiplatform library creates its host test compilation. */
    val hostTests: Provider<Boolean> = values.boolean(LvmKeys.ANDROID_HOST_TESTS, LvmDefaults.ANDROID_HOST_TESTS)
}

/** Build-wide values of `lvm { jvm { } }`. */
class JvmValues internal constructor(values: LvmValues) {
    /** Bytecode version of JVM and Android compilations, for example 17 or 21. */
    val target: Provider<Int> = values.int(LvmKeys.JVM_TARGET, LvmDefaults.JVM_TARGET)
}

/** Build-wide values of `lvm { kotlin { } }`. */
class KotlinValues internal constructor(values: LvmValues) {
    val warningsAsErrors: Provider<Boolean> =
        values.boolean(LvmKeys.KOTLIN_WARNINGS_AS_ERRORS, LvmDefaults.KOTLIN_WARNINGS_AS_ERRORS)

    /** The build-wide opt-ins, without those a module appends in its own `lvm { kotlin { } }`. */
    val optIns: Provider<List<String>> = values.list(LvmKeys.KOTLIN_OPT_INS, LvmDefaults.KOTLIN_OPT_INS)

    /** The build-wide compiler arguments, without those a module appends in its own `lvm { kotlin { } }`. */
    val freeCompilerArgs: Provider<List<String>> =
        values.list(LvmKeys.KOTLIN_FREE_COMPILER_ARGS, LvmDefaults.KOTLIN_FREE_COMPILER_ARGS)

    /** Strict explicit API mode for main compilations of libraries. */
    val explicitApi: Provider<Boolean> = values.boolean(LvmKeys.KOTLIN_EXPLICIT_API, LvmDefaults.KOTLIN_EXPLICIT_API)

    /** Whether LVM's plugins add `kotlin-test` to tests. */
    val testDependencies: Provider<Boolean> =
        values.boolean(LvmKeys.KOTLIN_TEST_DEPENDENCIES, LvmDefaults.KOTLIN_TEST_DEPENDENCIES)

    /** Whether modules use the flat source layout: `src`, `test`, `src@<name>` and `test@<name>`. */
    val flatLayout: Provider<Boolean> = values.boolean(LvmKeys.KOTLIN_FLAT_LAYOUT, LvmDefaults.KOTLIN_FLAT_LAYOUT)

    /**
     * How Kotlin compiles interface functions with a body for JVM and Android: `enable`, `no-compatibility` or
     * `disable`. Absent unless set, so Kotlin's own default applies.
     */
    val jvmDefault: Provider<String> = values.raw(LvmKeys.KOTLIN_JVM_DEFAULT).map(LvmValueParsing::jvmDefault)
}
