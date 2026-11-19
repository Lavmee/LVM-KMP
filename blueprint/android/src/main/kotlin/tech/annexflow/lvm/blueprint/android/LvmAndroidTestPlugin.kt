package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.lvmValues
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/**
 * An Android test module, such as a macrobenchmark or a baseline profile generator: AGP's test plugin with its Kotlin
 * built in. Its code is test code that instruments the app at `targetProjectPath`, so it gets neither unit tests nor
 * explicit API mode.
 *
 * Core library desugaring stays off: the module's APK only drives the app, which has its own desugaring, and a module
 * that calls Java APIs newer than its `minSdk` turns it on with the native `android { compileOptions { } }`.
 */
class LvmAndroidTestPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("com.android.test")
        val androidComponents = project.extensions.getByType(TestAndroidComponentsExtension::class.java)
        project.requireSupportedAgp(androidComponents)

        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlin = project.lvmKotlin()
        project.reportAndroidModule(
            "tech.annexflow.lvm.android.test",
            android,
            project.extensions.getByType(TestExtension::class.java),
        )

        // Defaults that go in now, before the build script runs, so values the script sets natively replace them.
        val buildWideJvmTarget = project.lvmValues.jvm.target.get()
        project.extensions.configure(TestExtension::class.java) {
            setJavaVersion(buildWideJvmTarget)
            testOptions.animationsDisabled = true
            excludeDuplicateMetadata()
        }

        // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
        // lvm fills in only what native DSL left unset or at AGP's default.
        androidComponents.finalizeDsl { extension ->
            extension.fillSdkAndNamespace(android)
            // With a preview level, AGP reports an API level for targetSdk, so it isn't null either.
            if (extension.defaultConfig.targetSdk == null) extension.defaultConfig.targetSdk = android.targetSdk.get()
            extension.fillTestInstrumentationRunner(android)
            extension.applyModuleJvmTarget(jvm, buildWideJvmTarget)
            project.configureSourceLayout(extension, kotlin.flatLayout.get())
        }

        project.configureKotlin(kotlin, project.lvmValues.kotlin)
        project.configureSourceLayoutCheck(project.extensions.getByType(TestExtension::class.java), kotlin)
    }
}
