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
    const val KOTLIN_FLAT_LAYOUT: Boolean = false
    val IOS_ARCHITECTURES: List<String> = listOf("iosArm64", "iosSimulatorArm64")
    val QUALITY_CONFIG: List<String> = emptyList()
    val QUALITY_SOURCE: List<String> = listOf("src")
    val QUALITY_SOURCE_FLAT: List<String> = listOf("src", "test", "src@*", "test@*")
    const val QUALITY_BUILD_UPON_DEFAULT_CONFIG: Boolean = true
    const val QUALITY_COMPOSE_RULES: Boolean = false
    val MAIN_CONTEXT_PLATFORMS: List<String> = listOf("android", "ios", "jvm")
    const val MAIN_CONTEXT_DEPENDENCY: Boolean = true
    const val COMPOSE_METRICS: Boolean = false
    const val COMPOSE_REPORTS: Boolean = false
    val COMPOSE_STABILITY_CONFIGURATION_FILES: List<String> = emptyList()
    const val PUBLISH_ARTIFACT_ID_PREFIX: String = ""
    const val PUBLISH_SIGNING: Boolean = true
}
