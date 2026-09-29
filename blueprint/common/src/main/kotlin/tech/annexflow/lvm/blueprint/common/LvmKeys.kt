package tech.annexflow.lvm.blueprint.common

/** Keys of build-wide values. As Gradle properties and forwarded settings values they carry the `lvm.` prefix. */
object LvmKeys {
    const val PREFIX: String = "lvm."

    const val ANDROID_COMPILE_SDK: String = "android.compileSdk"
    const val ANDROID_MIN_SDK: String = "android.minSdk"
    const val ANDROID_HOST_TESTS: String = "android.hostTests"
    const val JVM_TARGET: String = "jvm.target"
    const val KOTLIN_WARNINGS_AS_ERRORS: String = "kotlin.warningsAsErrors"
    const val KOTLIN_OPT_INS: String = "kotlin.optIns"
    const val KOTLIN_FREE_COMPILER_ARGS: String = "kotlin.freeCompilerArgs"
    const val KOTLIN_EXPLICIT_API: String = "kotlin.explicitApi"
    const val KOTLIN_TEST_DEPENDENCIES: String = "kotlin.testDependencies"
    const val IOS_ARCHITECTURES: String = "ios.architectures"
    const val QUALITY_CONFIG: String = "quality.config"
    const val QUALITY_SOURCE: String = "quality.source"
    const val QUALITY_BUILD_UPON_DEFAULT_CONFIG: String = "quality.buildUponDefaultConfig"
    const val QUALITY_COMPOSE_RULES: String = "quality.composeRules"

    fun property(key: String): String = PREFIX + key
}
