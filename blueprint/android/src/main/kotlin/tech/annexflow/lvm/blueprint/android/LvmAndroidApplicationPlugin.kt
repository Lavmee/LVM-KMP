package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValueParsing
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle
import tech.annexflow.lvm.blueprint.common.requireSupported

/** An Android application module: AGP's application plugin with its Kotlin built in. */
class LvmAndroidApplicationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("com.android.application")
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        val agp = androidComponents.pluginVersion
        project.requireSupported(
            tool = "Android Gradle plugin",
            actual = "${agp.major}.${agp.minor}.${agp.micro}",
            minimum = LvmBuildInfo.MIN_AGP,
            tested = LvmBuildInfo.TESTED_AGP,
        )

        val values = LvmValues(project)
        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlin = project.lvmKotlin()
        val application = project.lvm.extensions.create("application", ApplicationSection::class.java)
        application.applicationId.convention(values.raw(LvmKeys.APPLICATION_ID))
        application.versionCode.convention(
            values.raw(LvmKeys.APPLICATION_VERSION_CODE).map { LvmValueParsing.int(LvmKeys.APPLICATION_VERSION_CODE, it) },
        )
        application.versionName.convention(values.raw(LvmKeys.APPLICATION_VERSION_NAME))
        application.minify.convention(values.boolean(LvmKeys.APPLICATION_MINIFY, LvmDefaults.APPLICATION_MINIFY))
        application.desugaring.convention(values.boolean(LvmKeys.APPLICATION_DESUGARING, LvmDefaults.APPLICATION_DESUGARING))
        val proguardFiles = values.list(LvmKeys.APPLICATION_PROGUARD_FILES, LvmDefaults.APPLICATION_PROGUARD_FILES)
            .zip(application.proguardFiles) { buildWide, module -> buildWide + module }

        // Defaults that go in now, before the build script runs, so values the script sets natively replace them.
        // AGP throws when finalizeDsl reads the Java version, set or not, so lvm can't tell a native one there:
        // the build-wide version goes in here, and finalizeDsl applies only a module's own lvm value.
        val buildWideJvmTarget = values.int(LvmKeys.JVM_TARGET, LvmDefaults.JVM_TARGET).get()
        project.extensions.configure(ApplicationExtension::class.java) {
            compileOptions.sourceCompatibility = JavaVersion.toVersion(buildWideJvmTarget)
            compileOptions.targetCompatibility = JavaVersion.toVersion(buildWideJvmTarget)
            testOptions.animationsDisabled = true
            // Metadata that several jars carry under the same name and that no app needs at runtime.
            packaging.resources.excludes.addAll(listOf("/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES"))
        }

        // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
        // lvm fills in only what native DSL left unset or at AGP's default.
        androidComponents.finalizeDsl { extension ->
            configureSdk(extension, android)
            project.configureApplication(extension, application, proguardFiles)
            configureJava(extension, jvm, buildWideJvmTarget, application)
        }

        project.configureKotlin(kotlin, values)
        project.configureDesugaringDependency(application)
    }

    private fun configureSdk(extension: ApplicationExtension, android: AndroidSection) {
        val config = extension.defaultConfig
        if (extension.compileSdk == null && extension.compileSdkPreview == null) extension.compileSdk = android.compileSdk.get()
        // With a preview level, AGP reports an API level for minSdk and targetSdk, so they aren't null either.
        if (config.minSdk == null) config.minSdk = android.minSdk.get()
        if (config.targetSdk == null) config.targetSdk = android.targetSdk.get()
        if (extension.namespace == null) extension.namespace = android.namespace.get()
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

    private fun configureJava(
        extension: ApplicationExtension,
        jvm: JvmSection,
        buildWideJvmTarget: Int,
        application: ApplicationSection,
    ) {
        val options = extension.compileOptions
        // The section's convention is the build-wide value, so a different one is the module's own.
        val jvmTarget = jvm.target.get()
        if (jvmTarget != buildWideJvmTarget) {
            options.sourceCompatibility = JavaVersion.toVersion(jvmTarget)
            options.targetCompatibility = JavaVersion.toVersion(jvmTarget)
        }
        if (application.desugaring.get()) options.isCoreLibraryDesugaringEnabled = true
    }

    private fun Project.configureDesugaringDependency(application: ApplicationSection) {
        val desugarJdkLibs = dependencies.create(LvmBuildInfo.DESUGAR_JDK_LIBS)
        val dependencyIfEnabled = application.desugaring.map { enabled -> if (enabled) listOf(desugarJdkLibs) else emptyList() }
        configurations.named("coreLibraryDesugaring").configure { dependencies.addAllLater(dependencyIfEnabled) }
    }
}
