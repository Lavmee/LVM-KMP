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
    const val KOTLIN_FLAT_LAYOUT: String = "kotlin.flatLayout"
    const val IOS_ARCHITECTURES: String = "ios.architectures"
    const val QUALITY_CONFIG: String = "quality.config"
    const val QUALITY_SOURCE: String = "quality.source"
    const val QUALITY_BUILD_UPON_DEFAULT_CONFIG: String = "quality.buildUponDefaultConfig"
    const val QUALITY_COMPOSE_RULES: String = "quality.composeRules"
    const val MAIN_CONTEXT_PLATFORMS: String = "mainContext.platforms"
    const val MAIN_CONTEXT_DEPENDENCY: String = "mainContext.dependency"
    const val COMPOSE_METRICS: String = "compose.metrics"
    const val COMPOSE_REPORTS: String = "compose.reports"
    const val COMPOSE_STABILITY_CONFIGURATION_FILES: String = "compose.stabilityConfigurationFiles"
    const val PUBLISH_ARTIFACT_ID_PREFIX: String = "publish.artifactIdPrefix"
    const val PUBLISH_DESCRIPTION: String = "publish.description"
    const val PUBLISH_URL: String = "publish.url"
    const val PUBLISH_LICENSE_NAME: String = "publish.license.name"
    const val PUBLISH_LICENSE_URL: String = "publish.license.url"
    const val PUBLISH_DEVELOPER_ID: String = "publish.developer.id"
    const val PUBLISH_DEVELOPER_NAME: String = "publish.developer.name"
    const val PUBLISH_DEVELOPER_URL: String = "publish.developer.url"
    const val PUBLISH_SCM_URL: String = "publish.scm.url"
    const val PUBLISH_SCM_CONNECTION: String = "publish.scm.connection"
    const val PUBLISH_SCM_DEVELOPER_CONNECTION: String = "publish.scm.developerConnection"
    const val PUBLISH_SIGNING: String = "publish.signing"
    const val PUBLISH_ABI_ENABLED: String = "publish.abi.enabled"
    const val PUBLISH_ABI_EXCLUDED_NAMES: String = "publish.abi.excludedNames"
    const val PUBLISH_ABI_EXCLUDED_ANNOTATIONS: String = "publish.abi.excludedAnnotations"

    fun property(key: String): String = PREFIX + key
}
