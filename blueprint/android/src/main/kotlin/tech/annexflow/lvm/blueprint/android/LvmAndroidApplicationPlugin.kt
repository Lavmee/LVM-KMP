package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValueParsing
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.lvmValues
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/** An Android application module: AGP's application plugin with its Kotlin built in. */
class LvmAndroidApplicationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("com.android.application")
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        project.requireSupportedAgp(androidComponents)

        val values = LvmValues(project)
        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlin = project.lvmKotlin()
        val application = project.lvm.extensions.create("application", ApplicationSection::class.java)
        val applicationId = values.raw(LvmKeys.APPLICATION_ID)
        val versionCode = values.raw(LvmKeys.APPLICATION_VERSION_CODE)
            .map { LvmValueParsing.int(LvmKeys.APPLICATION_VERSION_CODE, it) }
        val versionName = values.raw(LvmKeys.APPLICATION_VERSION_NAME)
        val minify = values.boolean(LvmKeys.APPLICATION_MINIFY, LvmDefaults.APPLICATION_MINIFY)
        val desugaring = values.boolean(LvmKeys.APPLICATION_DESUGARING, LvmDefaults.APPLICATION_DESUGARING)
        val buildWideProguardFiles = values.list(LvmKeys.APPLICATION_PROGUARD_FILES, LvmDefaults.APPLICATION_PROGUARD_FILES)
        application.applicationId.convention(applicationId)
        application.versionCode.convention(versionCode)
        application.versionName.convention(versionName)
        application.minify.convention(minify)
        application.desugaring.convention(desugaring)
        val proguardFiles = buildWideProguardFiles.zip(application.proguardFiles) { buildWide, module -> buildWide + module }

        project.reportAndroidModule(
            "tech.annexflow.lvm.android.application",
            android,
            project.extensions.getByType(ApplicationExtension::class.java),
        )
        values.report(LvmKeys.APPLICATION_ID, application.applicationId, applicationId)
        values.report(LvmKeys.APPLICATION_VERSION_CODE, application.versionCode, versionCode)
        values.report(LvmKeys.APPLICATION_VERSION_NAME, application.versionName, versionName)
        values.report(LvmKeys.APPLICATION_MINIFY, application.minify, minify)
        values.report(LvmKeys.APPLICATION_PROGUARD_FILES, proguardFiles, buildWideProguardFiles)
        values.report(LvmKeys.APPLICATION_DESUGARING, application.desugaring, desugaring)

        // Defaults that go in now, before the build script runs, so values the script sets natively replace them.
        val buildWideJvmTarget = project.lvmValues.jvm.target.get()
        project.extensions.configure(ApplicationExtension::class.java) {
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
            extension.enableDesugaring(application.desugaring.get())
            project.configureApplication(extension, application, proguardFiles)
            project.configureSourceLayout(extension, kotlin.flatLayout.get())
        }

        project.configureKotlin(kotlin, project.lvmValues.kotlin)
        project.addKotlinTestToUnitTests(kotlin)
        project.addDesugaringDependency(application.desugaring)
        project.configureSourceLayoutCheck(project.extensions.getByType(ApplicationExtension::class.java), kotlin)
    }

    private fun Project.configureApplication(
        extension: ApplicationExtension,
        application: ApplicationSection,
        proguardFiles: Provider<List<String>>,
    ) {
        val config = extension.defaultConfig
        if (config.applicationId == null) config.applicationId = application.applicationId.orNull
        if (config.versionCode == null) config.versionCode = application.versionCode.orNull
        if (config.versionName == null) config.versionName = application.versionName.orNull

        val release = extension.buildTypes.getByName("release")
        if (application.minify.get()) {
            release.isMinifyEnabled = true
            release.isShrinkResources = true
        }
        if (release.proguardFiles.isEmpty()) {
            release.proguardFiles(extension.getDefaultProguardFile("proguard-android-optimize.txt"))
            // The default list names proguard-rules.pro, which a module may not have.
            proguardFiles.get().map(::file).filter { it.isFile }.forEach { release.proguardFiles(it) }
        }
    }
}
