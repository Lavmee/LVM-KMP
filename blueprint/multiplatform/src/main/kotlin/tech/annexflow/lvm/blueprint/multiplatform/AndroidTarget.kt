package tech.annexflow.lvm.blueprint.multiplatform

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmCompatibility
import tech.annexflow.lvm.blueprint.common.requireSupported

/** The only file that references AGP classes, so projects without AGP never load them. */
internal fun Project.configureAndroidTarget(kotlin: KotlinMultiplatformExtension, section: AndroidSection, spec: AndroidTargetSpec) {
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
    (kotlin as ExtensionAware).extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
        if (hostTests) withHostTest { }
    }

    // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
    // Values set with native DSL (kotlin { android { minSdk = … } }) are left alone.
    androidComponents.finalizeDsl { android ->
        if (android.compileSdk == null) android.compileSdk = section.compileSdk.get()
        if (android.minSdk == null) android.minSdk = section.minSdk.get()
        if (android.namespace == null) android.namespace = section.namespace.get()
    }
}
