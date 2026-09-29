package tech.annexflow.lvm.blueprint.common

/** Values used when neither a module, a Gradle property nor settings set one. */
object LvmDefaults {
    const val ANDROID_COMPILE_SDK: Int = 37
    const val ANDROID_MIN_SDK: Int = 24
    const val ANDROID_HOST_TESTS: Boolean = true
    const val JVM_TARGET: Int = 21
    const val KOTLIN_WARNINGS_AS_ERRORS: Boolean = false
    val KOTLIN_OPT_INS: List<String> = emptyList()
    val KOTLIN_FREE_COMPILER_ARGS: List<String> = emptyList()
    const val KOTLIN_EXPLICIT_API: Boolean = false
    const val KOTLIN_TEST_DEPENDENCIES: Boolean = true
    val IOS_ARCHITECTURES: List<String> = listOf("iosArm64", "iosSimulatorArm64")
    val QUALITY_CONFIG: List<String> = emptyList()
    val QUALITY_SOURCE: List<String> = listOf("src")
    const val QUALITY_BUILD_UPON_DEFAULT_CONFIG: Boolean = true
    const val QUALITY_COMPOSE_RULES: Boolean = false
}
