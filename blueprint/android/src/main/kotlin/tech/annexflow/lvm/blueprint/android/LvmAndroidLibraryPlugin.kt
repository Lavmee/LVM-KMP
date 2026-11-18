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
import tech.annexflow.lvm.blueprint.common.lvmValues
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
        val desugaring = values.boolean(LvmKeys.LIBRARY_DESUGARING, LvmDefaults.LIBRARY_DESUGARING)
        val resourcePrefix = values.boolean(LvmKeys.LIBRARY_RESOURCE_PREFIX, LvmDefaults.LIBRARY_RESOURCE_PREFIX)
        val buildWideDroppedSegments = values.list(
            LvmKeys.LIBRARY_RESOURCE_PREFIX_DROPPED_SEGMENTS,
            LvmDefaults.LIBRARY_RESOURCE_PREFIX_DROPPED_SEGMENTS,
        )
        val skipEmptyAndroidTest = values.boolean(LvmKeys.LIBRARY_SKIP_EMPTY_ANDROID_TEST, LvmDefaults.LIBRARY_SKIP_EMPTY_ANDROID_TEST)
        val buildWideConsumerProguardFiles =
            values.list(LvmKeys.LIBRARY_CONSUMER_PROGUARD_FILES, LvmDefaults.LIBRARY_CONSUMER_PROGUARD_FILES)
        library.desugaring.convention(desugaring)
        library.resourcePrefix.convention(resourcePrefix)
        library.skipEmptyAndroidTest.convention(skipEmptyAndroidTest)
        val consumerProguardFiles = buildWideConsumerProguardFiles
            .zip(library.consumerProguardFiles) { buildWide, module -> buildWide + module }
        val droppedSegments = buildWideDroppedSegments
            .zip(library.resourcePrefixDroppedSegments) { buildWide, module -> buildWide + module }

        project.reportAndroidModule(
            "tech.annexflow.lvm.android.library",
            android,
            project.extensions.getByType(LibraryExtension::class.java),
        )
        values.report(LvmKeys.LIBRARY_DESUGARING, library.desugaring, desugaring)
        values.report(LvmKeys.LIBRARY_CONSUMER_PROGUARD_FILES, consumerProguardFiles, buildWideConsumerProguardFiles)
        values.report(LvmKeys.LIBRARY_RESOURCE_PREFIX, library.resourcePrefix, resourcePrefix)
        values.report(LvmKeys.LIBRARY_RESOURCE_PREFIX_DROPPED_SEGMENTS, droppedSegments, buildWideDroppedSegments)
        values.report(LvmKeys.LIBRARY_SKIP_EMPTY_ANDROID_TEST, library.skipEmptyAndroidTest, skipEmptyAndroidTest)

        // Defaults that go in now, before the build script runs, so values the script sets natively replace them.
        val buildWideJvmTarget = project.lvmValues.jvm.target.get()
        project.extensions.configure(LibraryExtension::class.java) {
            setJavaVersion(buildWideJvmTarget)
            testOptions.animationsDisabled = true
            excludeDuplicateMetadata()
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
                extension.resourcePrefix = defaultResourcePrefix(project.path, droppedSegments.get())
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

        project.configureKotlin(kotlin, project.lvmValues.kotlin)
        project.addKotlinTestToUnitTests(kotlin)
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

/** A segment of a module path, as [defaultResourcePrefix] drops them: a run of letters, digits and `_`. */
private val PATH_SEGMENT = Regex("[A-Za-z0-9_]+")

/**
 * The resource prefix of the module at [projectPath]: each part of the path lowercased, with every character other
 * than `a`-`z`, a digit or `_` replaced by `_`, the parts joined by `_` and followed by `_`.
 * `:feature:chat-history:ui` -> `feature_chat_history_ui_`. The root project, whose path has no parts, has none.
 *
 * Segments of the path in [droppedSegments], compared without case, are left out first: from the end, while the last
 * segment is one of them and more than one is left. A segment is a run of letters, digits and `_`, so with `ui` and
 * `sky`, `:feature:chat:ui-sky` -> `feature_chat_`. What remains of the path gives the prefix as above, so with
 * nothing to drop the prefix is the same as without [droppedSegments].
 */
internal fun defaultResourcePrefix(projectPath: String, droppedSegments: List<String> = emptyList()): String? {
    val parts = withoutDroppedSegments(projectPath, droppedSegments).split(':').filter(String::isNotEmpty)
    if (parts.isEmpty()) return null
    return parts.joinToString("_", postfix = "_") { it.lowercase().replace(NOT_IN_RESOURCE_NAMES, "_") }
}

/** [projectPath] up to the end of its last segment that is not in [dropped], or up to its first segment. */
private fun withoutDroppedSegments(projectPath: String, dropped: List<String>): String {
    val droppedLowercase = dropped.map(String::lowercase).toSet()
    val segments = PATH_SEGMENT.findAll(projectPath).toList()
    var kept = segments.size
    while (kept > 1 && segments[kept - 1].value.lowercase() in droppedLowercase) kept--
    if (kept == segments.size) return projectPath
    return projectPath.substring(0, segments[kept - 1].range.last + 1)
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
