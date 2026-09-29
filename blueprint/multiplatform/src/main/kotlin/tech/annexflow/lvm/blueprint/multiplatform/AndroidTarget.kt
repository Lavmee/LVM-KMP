package tech.annexflow.lvm.blueprint.multiplatform

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmCompatibility
import tech.annexflow.lvm.blueprint.common.requireSupported

/** The only file that references AGP classes, so projects without AGP never load them. */
internal fun Project.configureAndroidTarget(
    kotlin: KotlinMultiplatformExtension,
    section: AndroidSection,
    jvm: JvmSection,
    spec: AndroidTargetSpec,
) {
    pluginManager.apply("com.android.kotlin.multiplatform.library")

    val androidComponents = extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
    val agp = androidComponents.pluginVersion
    requireSupported(
        tool = "Android Gradle plugin",
        actual = "${agp.major}.${agp.minor}.${agp.micro}",
        minimum = LvmCompatibility.MIN_AGP,
        tested = LvmBuildInfo.TESTED_AGP,
    )

    val hostTests = spec.hostTests.get()
    val jvmTarget = jvm.kotlinJvmTarget()
    (kotlin as ExtensionAware).extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
        if (hostTests) withHostTest { }
        // A convention, like the one of JVM targets, so kotlin { android { compilerOptions { jvmTarget = … } } } wins.
        compilerOptions.jvmTarget.convention(jvmTarget)
    }

    // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
    // Values set with native DSL, preview levels included, win: lvm fills in only what is still unset.
    // The Kotlin DSL accessor kotlin { android { … } } exists only when the module also lists
    // com.android.kotlin.multiplatform.library in plugins { }; the apply above then does nothing.
    androidComponents.finalizeDsl { android ->
        if (android.compileSdk == null && android.compileSdkPreview == null) android.compileSdk = section.compileSdk.get()
        if (android.minSdk == null && android.minSdkPreview == null) android.minSdk = section.minSdk.get()
        if (android.namespace == null) android.namespace = section.namespace.get()
    }
}
