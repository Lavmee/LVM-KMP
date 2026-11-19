package tech.annexflow.lvm.blueprint.multiplatform

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.Component
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.reportAndroidValues
import tech.annexflow.lvm.blueprint.common.reportTool
import tech.annexflow.lvm.blueprint.common.requireSupported

/** The only file that references AGP classes, so projects without AGP never load them. */
internal fun Project.configureAndroidTarget(
    kotlin: KotlinMultiplatformExtension,
    section: AndroidSection,
    jvm: JvmSection,
    kotlinSection: KotlinSection,
    spec: AndroidTargetSpec,
) {
    pluginManager.apply("com.android.kotlin.multiplatform.library")

    val androidComponents = extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
    val agp = androidComponents.pluginVersion
    val agpVersion = "${agp.major}.${agp.minor}.${agp.micro}"
    requireSupported(
        tool = "Android Gradle plugin",
        actual = agpVersion,
        minimum = LvmBuildInfo.MIN_AGP,
        tested = LvmBuildInfo.TESTED_AGP,
    )
    reportTool("agp", agpVersion, LvmBuildInfo.MIN_AGP, LvmBuildInfo.TESTED_AGP)
    reportAndroidValues(section)

    val hostTests = spec.hostTests.get()
    val jvmTarget = jvm.kotlinJvmTarget()
    val jvmDefault = kotlinSection.jvmDefaultMode()
    val target = (kotlin as ExtensionAware).extensions.getByType(KotlinMultiplatformAndroidLibraryTarget::class.java)
    if (hostTests) target.withHostTest { }
    // Conventions, like those of JVM targets, so kotlin { android { compilerOptions { jvmTarget = ... } } } wins.
    target.compilerOptions.jvmTarget.convention(jvmTarget)
    target.compilerOptions.jvmDefault.convention(jvmDefault)
    configureJavaSources(androidComponents, target, kotlinSection.flatLayout)

    // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
    // Values set with native DSL, preview levels included, win: lvm fills in only what is still unset.
    // The Kotlin DSL accessor kotlin { android { ... } } exists only when the module also lists
    // com.android.kotlin.multiplatform.library in plugins { }; the apply above then does nothing.
    androidComponents.finalizeDsl { android ->
        android.compileSdk { fillCompileSdk(section) }
        if (android.minSdk == null && android.minSdkPreview == null) android.minSdk = section.minSdk.get()
        if (android.namespace == null) android.namespace = section.namespace.get()
    }
}

/**
 * Sets the compile SDK level of [section], with its minor level when there is one. The spec starts with the version the
 * build script set, so a level, a minor level, a preview or an add-on set there stays.
 */
private fun CompileSdkSpec.fillCompileSdk(section: AndroidSection) {
    if (version != null) return
    val level = section.compileSdk.get()
    val minor = section.compileSdkMinor.orNull
    version = if (minor == null) release(level) else release(level) { minorApiLevel = minor }
}

/**
 * With the flat layout, javac compiles the Java files of `src@android/`, `test@androidHost/` and `test@androidDevice/`
 * once the target calls `withJava()`. Without it, AGP compiles no Java, so a Java file there fails the build instead of
 * going missing at runtime.
 */
private fun Project.configureJavaSources(
    androidComponents: KotlinMultiplatformAndroidComponentsExtension,
    target: KotlinMultiplatformAndroidLibraryTarget,
    flatLayout: Provider<Boolean>,
) {
    val moduleDirectory = layout.projectDirectory
    val modulePath = path
    androidComponents.onVariants { variant ->
        if (!flatLayout.get()) return@onVariants
        // Each component builds one compilation, whose default source set gives the flat directory.
        val sourceSets = target.compilations.associate { it.componentName to it.defaultSourceSet.name }
        val components = listOf<Component>(variant) + variant.hostTests.values + variant.deviceTests.values
        for (component in components) {
            val directory = sourceSets[component.name]?.let(::flatSourceDirectory) ?: continue
            val java = component.sources.java
            if (java != null) {
                java.addStaticSourceDirectory(directory)
            } else {
                requireNoJavaFiles(
                    modulePath,
                    moduleDirectory,
                    directory,
                    "Call withJava() in kotlin { android { } }, so that javac compiles them.",
                )
            }
        }
    }
}
