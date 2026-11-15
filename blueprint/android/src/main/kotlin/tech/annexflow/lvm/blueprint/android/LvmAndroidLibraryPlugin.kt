package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvm
import tech.annexflow.lvm.blueprint.common.lvmAndroid
import tech.annexflow.lvm.blueprint.common.lvmJvm
import tech.annexflow.lvm.blueprint.common.lvmKotlin
import tech.annexflow.lvm.blueprint.common.requireMinimumGradle

/** An Android library module without Kotlin Multiplatform: AGP's library plugin with its Kotlin built in. */
class LvmAndroidLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        requireMinimumGradle()
        project.pluginManager.apply("com.android.library")
        val androidComponents = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
        project.requireSupportedAgp(androidComponents)

        val values = LvmValues(project)
        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlin = project.lvmKotlin()
        val library = project.lvm.extensions.create("library", LibrarySection::class.java)
        library.desugaring.convention(values.boolean(LvmKeys.LIBRARY_DESUGARING, LvmDefaults.LIBRARY_DESUGARING))
        library.resourcePrefix.convention(values.boolean(LvmKeys.LIBRARY_RESOURCE_PREFIX, LvmDefaults.LIBRARY_RESOURCE_PREFIX))
        library.skipEmptyAndroidTest.convention(
            values.boolean(LvmKeys.LIBRARY_SKIP_EMPTY_ANDROID_TEST, LvmDefaults.LIBRARY_SKIP_EMPTY_ANDROID_TEST),
        )
        val consumerProguardFiles = values.list(LvmKeys.LIBRARY_CONSUMER_PROGUARD_FILES, LvmDefaults.LIBRARY_CONSUMER_PROGUARD_FILES)
            .zip(library.consumerProguardFiles) { buildWide, module -> buildWide + module }

        // Defaults that go in now, before the build script runs, so values the script sets natively replace them.
        val buildWideJvmTarget = values.int(LvmKeys.JVM_TARGET, LvmDefaults.JVM_TARGET).get()
        project.extensions.configure(LibraryExtension::class.java) {
            setJavaVersion(buildWideJvmTarget)
            testOptions.animationsDisabled = true
        }

        // finalizeDsl runs after the build script, so module values set anywhere in lvm { } are visible here.
        // lvm fills in only what native DSL left unset or at AGP's default.
        androidComponents.finalizeDsl { extension ->
            extension.fillSdkAndNamespace(android)
            fillTargetSdk(extension, android)
            extension.fillTestInstrumentationRunner(android)
            extension.applyModuleJvmTarget(jvm, buildWideJvmTarget)
            extension.enableDesugaring(library.desugaring.get())
            project.fillConsumerProguardFiles(extension, consumerProguardFiles)
            if (extension.resourcePrefix == null && library.resourcePrefix.get()) {
                extension.resourcePrefix = defaultResourcePrefix(project.path)
            }
            project.configureSourceLayout(extension, kotlin.flatLayout.get())
        }

        // beforeVariants runs after the build script and before AGP creates the tasks of instrumented tests.
        val skipAndroidTest = lazy {
            library.skipEmptyAndroidTest.get() && !hasAndroidTestSources(project.projectDir, kotlin.flatLayout.get())
        }
        androidComponents.beforeVariants { variant ->
            if (skipAndroidTest.value) variant.androidTest.enable = false
        }

        project.configureKotlin(kotlin, values)
        project.configureExplicitApi(kotlin, androidComponents)
        project.addDesugaringDependency(library.desugaring)
    }

    /** AGP has no target SDK level for a library itself, only for its instrumented tests and lint. */
    private fun fillTargetSdk(extension: LibraryExtension, android: AndroidSection) {
        // With a preview level, AGP reports an API level for both, so they aren't null either.
        if (extension.testOptions.targetSdk == null) extension.testOptions.targetSdk = android.targetSdk.get()
        if (extension.lint.targetSdk == null) extension.lint.targetSdk = android.targetSdk.get()
    }

    private fun Project.fillConsumerProguardFiles(extension: LibraryExtension, files: Provider<List<String>>) {
        val config = extension.defaultConfig
        if (config.consumerProguardFiles.isNotEmpty()) return
        // The default list names consumer-rules.pro, which a module may not have.
        files.get().map(::file).filter(File::isFile).forEach { config.consumerProguardFiles(it) }
    }
}

private val NOT_IN_RESOURCE_NAMES = Regex("[^a-z0-9_]")

/**
 * The resource prefix of the module at [projectPath]: each segment lowercased, with every character other than `a`-`z`,
 * a digit or `_` replaced by `_`, the segments joined by `_` and followed by `_`. `:feature:chat-history:ui` ->
 * `feature_chat_history_ui_`. The root project, whose path has no segments, has none.
 */
internal fun defaultResourcePrefix(projectPath: String): String? {
    val segments = projectPath.split(':').filter(String::isNotEmpty)
    if (segments.isEmpty()) return null
    return segments.joinToString("_", postfix = "_") { it.lowercase().replace(NOT_IN_RESOURCE_NAMES, "_") }
}

private const val ANDROID_TEST = "androidTest"

/**
 * Whether the module has sources of instrumented tests: `androidTest/` or `androidTest@<name>/` with the flat layout,
 * and `src/androidTest/` or `src/androidTest<Name>/` in either layout, where the manifest and resources of instrumented
 * tests stay.
 */
private fun hasAndroidTestSources(projectDir: File, flatLayout: Boolean): Boolean {
    val flat = flatLayout && projectDir.hasDirectory { it == ANDROID_TEST || it.startsWith("$ANDROID_TEST@") }
    return flat || projectDir.resolve("src").hasDirectory(::isAndroidTestSourceSet)
}

private fun isAndroidTestSourceSet(name: String): Boolean =
    name == ANDROID_TEST || name.startsWith(ANDROID_TEST) && name[ANDROID_TEST.length].isUpperCase()

private fun File.hasDirectory(predicate: (String) -> Boolean): Boolean =
    listFiles()?.any { it.isDirectory && predicate(it.name) } == true
