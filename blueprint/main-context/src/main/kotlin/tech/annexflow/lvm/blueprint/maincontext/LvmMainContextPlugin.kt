package tech.annexflow.lvm.blueprint.maincontext

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.reportPlugin
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/**
 * Runs the `@MainContext` compiler plugin of the same LVM version on the compilations of the selected platforms, in
 * Kotlin Multiplatform, Android and Kotlin/JVM modules.
 */
class LvmMainContextPlugin : KotlinCompilerPluginSupportPlugin {

    private lateinit var section: MainContextSection
    private var kotlinChecked = false

    override fun apply(target: Project) {
        requireMinimumGradle()
        val values = LvmValues(target)
        section = target.lvm.extensions.create("mainContext", MainContextSection::class.java)
        val platforms = values.list(LvmKeys.MAIN_CONTEXT_PLATFORMS, LvmDefaults.MAIN_CONTEXT_PLATFORMS)
        val dependency = values.boolean(LvmKeys.MAIN_CONTEXT_DEPENDENCY, LvmDefaults.MAIN_CONTEXT_DEPENDENCY)
        section.platforms.convention(platforms)
        section.dependency.convention(dependency)
        target.reportPlugin("tech.annexflow.lvm.main-context")
        values.report(LvmKeys.MAIN_CONTEXT_PLATFORMS, section.platforms, platforms)
        values.report(LvmKeys.MAIN_CONTEXT_DEPENDENCY, section.dependency, dependency)

        val annotation = target.dependencies.create(
            "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_ARTIFACT}:${LvmBuildInfo.VERSION}",
        )
        val annotationIfEnabled = section.dependency.map { enabled -> if (enabled) listOf(annotation) else emptyList() }
        // Kotlin Multiplatform shares the annotation with every target through commonMain. AGP's built-in Kotlin, which
        // modules without Kotlin Multiplatform use, has no plugin ID of its own, so Android modules are found by AGP's.
        target.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM) {
            target.addLater("commonMainImplementation", annotationIfEnabled)
        }
        target.pluginManager.withPlugin(ANDROID) { target.addLater("implementation", annotationIfEnabled) }
        target.pluginManager.withPlugin(KOTLIN_JVM) { target.addLater("implementation", annotationIfEnabled) }
    }

    // The Kotlin Gradle plugin asks once the build script has run, so values set anywhere in lvm { } count.
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val platforms = section.platforms.get()
        val unknown = platforms - LvmDefaults.MAIN_CONTEXT_PLATFORMS.toSet()
        if (unknown.isNotEmpty()) {
            throw InvalidUserDataException(
                "Unknown platform '${unknown.first()}' in lvm { mainContext { platforms } } or lvm.mainContext.platforms. " +
                    "Supported: ${LvmDefaults.MAIN_CONTEXT_PLATFORMS.joinToString()}.",
            )
        }
        val applicable = kotlinCompilation.lvmPlatform() in platforms
        // The version matters only where the compiler plugin runs. One check per module keeps the warning to one.
        if (applicable && !kotlinChecked) {
            val project = kotlinCompilation.target.project
            requireMainContextKotlin(project.getKotlinPluginVersion())?.let(project.logger::warn)
            kotlinChecked = true
        }
        return applicable
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(LvmBuildInfo.GROUP, LvmBuildInfo.MAIN_CONTEXT_COMPILER_ARTIFACT, LvmBuildInfo.VERSION)
}

/** Adds [added] to the configuration [name], if the module has one. */
private fun Project.addLater(name: String, added: Provider<List<Dependency>>) {
    configurations.named { it == name }.configureEach { dependencies.addAllLater(added) }
}

/** The platform of [KotlinCompilation] as `lvm { mainContext { platforms } }` names it, or `null` for any other. */
private fun KotlinCompilation<*>.lvmPlatform(): String? = when (platformType) {
    KotlinPlatformType.androidJvm -> "android"
    KotlinPlatformType.jvm -> "jvm"
    KotlinPlatformType.native -> "ios".takeIf { (target as KotlinNativeTarget).konanTarget.family == Family.IOS }
    else -> null
}

/**
 * Fails unless [actual] is the Kotlin version that the compiler plugin was built with, or a version whose patch number
 * differs from it only in the last digit, as the bugfix release 2.4.21 does from 2.4.20. Returns a warning for the
 * latter. Compiler plugin APIs can change in any other release.
 */
internal fun requireMainContextKotlin(actual: String): String? {
    val built = LvmBuildInfo.TESTED_KOTLIN
    val line = checkNotNull(bugfixLine(built)) { "Kotlin $built is not a major.minor.patch version." }
    return when {
        actual == built -> null
        bugfixLine(actual) == line ->
            "tech.annexflow.lvm.main-context runs its compiler plugin, built with Kotlin $built, on Kotlin $actual. " +
                "If compilation fails, use the Kotlin Gradle plugin $built, or the LVM release built for Kotlin $actual."
        else -> {
            val (major, minor, tens) = line
            throw GradleException(
                "tech.annexflow.lvm.main-context works only with Kotlin $major.$minor.${tens * 10} to " +
                    "$major.$minor.${tens * 10 + 9}: its compiler plugin was built with Kotlin $built and runs on its " +
                    "bugfix releases. This build uses Kotlin $actual. Use the Kotlin Gradle plugin $built, " +
                    "or the LVM release built for Kotlin $actual.",
            )
        }
    }
}

/** The major and minor number and the patch number without its last digit, which bugfix releases share. */
private fun bugfixLine(version: String): List<Int>? {
    val numbers = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
    if (numbers.size != 3) return null
    val (major, minor, patch) = numbers
    return listOf(major, minor, patch / 10)
}

private const val KOTLIN_MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"
private const val KOTLIN_JVM = "org.jetbrains.kotlin.jvm"

/** Applied by every AGP plugin: application, library, test and dynamic feature. */
private const val ANDROID = "com.android.base"

/** Must match the `pluginId` of the compiler plugin's registrar. */
private const val COMPILER_PLUGIN_ID = "tech.annexflow.lvm.maincontext"
