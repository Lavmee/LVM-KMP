package tech.annexflow.lvm.blueprint.maincontext

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
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
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/** Runs the `@MainContext` compiler plugin of the same LVM version on the compilations of the selected platforms. */
class LvmMainContextPlugin : KotlinCompilerPluginSupportPlugin {

    private lateinit var section: MainContextSection

    override fun apply(target: Project) {
        requireMinimumGradle()
        val values = LvmValues(target)
        section = target.lvm.extensions.create("mainContext", MainContextSection::class.java)
        section.platforms.convention(values.list(LvmKeys.MAIN_CONTEXT_PLATFORMS, LvmDefaults.MAIN_CONTEXT_PLATFORMS))
        section.dependency.convention(values.boolean(LvmKeys.MAIN_CONTEXT_DEPENDENCY, LvmDefaults.MAIN_CONTEXT_DEPENDENCY))

        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            requireMainContextKotlin(target.getKotlinPluginVersion())
            val annotation = target.dependencies.create(
                "${LvmBuildInfo.GROUP}:${LvmBuildInfo.MAIN_CONTEXT_ARTIFACT}:${LvmBuildInfo.VERSION}",
            )
            val annotationIfEnabled = section.dependency.map { enabled -> if (enabled) listOf(annotation) else emptyList() }
            target.configurations.named("commonMainImplementation").configure {
                dependencies.addAllLater(annotationIfEnabled)
            }
        }
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
        return kotlinCompilation.lvmPlatform() in platforms
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(LvmBuildInfo.GROUP, LvmBuildInfo.MAIN_CONTEXT_COMPILER_ARTIFACT, LvmBuildInfo.VERSION)
}

/** The platform of [KotlinCompilation] as `lvm { mainContext { platforms } }` names it, or `null` for any other. */
private fun KotlinCompilation<*>.lvmPlatform(): String? = when (platformType) {
    KotlinPlatformType.androidJvm -> "android"
    KotlinPlatformType.jvm -> "jvm"
    KotlinPlatformType.native -> "ios".takeIf { (target as KotlinNativeTarget).konanTarget.family == Family.IOS }
    else -> null
}

/**
 * Fails unless [actual] is the Kotlin version that the compiler plugin was built with. Compiler plugin APIs change in
 * every Kotlin release, patch releases included.
 */
internal fun requireMainContextKotlin(actual: String) {
    val required = LvmBuildInfo.TESTED_KOTLIN
    if (actual != required) {
        throw GradleException(
            "tech.annexflow.lvm.main-context works only with Kotlin $required, the version its compiler plugin was " +
                "built with, and this build uses Kotlin $actual. Use the Kotlin Gradle plugin $required, " +
                "or the LVM release built for Kotlin $actual.",
        )
    }
}

/** Must match the `pluginId` of the compiler plugin's registrar. */
private const val COMPILER_PLUGIN_ID = "tech.annexflow.lvm.maincontext"
