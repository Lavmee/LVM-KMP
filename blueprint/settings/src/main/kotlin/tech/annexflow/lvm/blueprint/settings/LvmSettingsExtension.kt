package tech.annexflow.lvm.blueprint.settings

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValueParsing

/** `lvm { }` in settings.gradle.kts: build-wide defaults for every project, plus the catalog and repositories. */
abstract class LvmSettingsExtension @Inject constructor(objects: ObjectFactory) {

    /** Creates a version catalog from `tech.annexflow.lvm:lvm-versions`. */
    abstract val catalog: Property<Boolean>

    /** Can't be `lvm`: every catalog is also a project extension, and `lvm` is the `lvm { }` block. */
    abstract val catalogName: Property<String>

    abstract val catalogVersion: Property<String>

    /** Adds `google()` and `mavenCentral()` to `dependencyResolutionManagement`. */
    abstract val repositories: Property<Boolean>

    /** Raw build-wide values by key, for example `values.put("android.minSdk", "26")`. Typed sections win over these. */
    abstract val values: MapProperty<String, String>

    val android: AndroidDefaults = objects.newInstance(AndroidDefaults::class.java)
    val jvm: JvmDefaults = objects.newInstance(JvmDefaults::class.java)
    val kotlin: KotlinDefaults = objects.newInstance(KotlinDefaults::class.java)
    val ios: IosDefaults = objects.newInstance(IosDefaults::class.java)
    val quality: QualityDefaults = objects.newInstance(QualityDefaults::class.java)
    val mainContext: MainContextDefaults = objects.newInstance(MainContextDefaults::class.java)
    val compose: ComposeDefaults = objects.newInstance(ComposeDefaults::class.java)
    val publish: PublishDefaults = objects.newInstance(PublishDefaults::class.java)
    val application: ApplicationDefaults = objects.newInstance(ApplicationDefaults::class.java)
    val library: LibraryDefaults = objects.newInstance(LibraryDefaults::class.java)

    fun android(action: Action<AndroidDefaults>) = action.execute(android)
    fun jvm(action: Action<JvmDefaults>) = action.execute(jvm)
    fun kotlin(action: Action<KotlinDefaults>) = action.execute(kotlin)
    fun ios(action: Action<IosDefaults>) = action.execute(ios)
    fun quality(action: Action<QualityDefaults>) = action.execute(quality)
    fun mainContext(action: Action<MainContextDefaults>) = action.execute(mainContext)
    fun compose(action: Action<ComposeDefaults>) = action.execute(compose)
    fun publish(action: Action<PublishDefaults>) = action.execute(publish)
    fun application(action: Action<ApplicationDefaults>) = action.execute(application)
    fun library(action: Action<LibraryDefaults>) = action.execute(library)

    /** Every value that was set, as `lvm.<key>` -> string. A list set to `emptyList()` is forwarded as an empty string. */
    internal fun forwardedValues(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        values.get().forEach { (key, value) -> result[LvmKeys.property(key)] = value }

        fun scalar(key: String, value: Provider<*>) {
            value.orNull?.let { result[LvmKeys.property(key)] = it.toString() }
        }

        fun list(key: String, value: ListProperty<String>) {
            value.orNull?.let { result[LvmKeys.property(key)] = LvmValueParsing.joinList(it) }
        }

        scalar(LvmKeys.ANDROID_COMPILE_SDK, android.compileSdk)
        scalar(LvmKeys.ANDROID_MIN_SDK, android.minSdk)
        scalar(LvmKeys.ANDROID_TARGET_SDK, android.targetSdk)
        scalar(LvmKeys.ANDROID_TEST_INSTRUMENTATION_RUNNER, android.testInstrumentationRunner)
        scalar(LvmKeys.ANDROID_HOST_TESTS, android.hostTests)
        scalar(LvmKeys.JVM_TARGET, jvm.target)
        scalar(LvmKeys.KOTLIN_WARNINGS_AS_ERRORS, kotlin.warningsAsErrors)
        list(LvmKeys.KOTLIN_OPT_INS, kotlin.optIns)
        list(LvmKeys.KOTLIN_FREE_COMPILER_ARGS, kotlin.freeCompilerArgs)
        scalar(LvmKeys.KOTLIN_EXPLICIT_API, kotlin.explicitApi)
        scalar(LvmKeys.KOTLIN_TEST_DEPENDENCIES, kotlin.testDependencies)
        scalar(LvmKeys.KOTLIN_FLAT_LAYOUT, kotlin.flatLayout)
        scalar(LvmKeys.KOTLIN_JVM_DEFAULT, kotlin.jvmDefault)
        list(LvmKeys.IOS_ARCHITECTURES, ios.architectures)
        list(LvmKeys.QUALITY_CONFIG, quality.config)
        list(LvmKeys.QUALITY_SOURCE, quality.source)
        scalar(LvmKeys.QUALITY_BUILD_UPON_DEFAULT_CONFIG, quality.buildUponDefaultConfig)
        scalar(LvmKeys.QUALITY_COMPOSE_RULES, quality.composeRules)
        list(LvmKeys.MAIN_CONTEXT_PLATFORMS, mainContext.platforms)
        scalar(LvmKeys.MAIN_CONTEXT_DEPENDENCY, mainContext.dependency)
        scalar(LvmKeys.COMPOSE_METRICS, compose.metrics)
        scalar(LvmKeys.COMPOSE_REPORTS, compose.reports)
        list(LvmKeys.COMPOSE_STABILITY_CONFIGURATION_FILES, compose.stabilityConfigurationFiles)
        scalar(LvmKeys.PUBLISH_ARTIFACT_ID_PREFIX, publish.artifactIdPrefix)
        scalar(LvmKeys.PUBLISH_DESCRIPTION, publish.description)
        scalar(LvmKeys.PUBLISH_URL, publish.url)
        scalar(LvmKeys.PUBLISH_LICENSE_NAME, publish.license.name)
        scalar(LvmKeys.PUBLISH_LICENSE_URL, publish.license.url)
        scalar(LvmKeys.PUBLISH_DEVELOPER_ID, publish.developer.id)
        scalar(LvmKeys.PUBLISH_DEVELOPER_NAME, publish.developer.name)
        scalar(LvmKeys.PUBLISH_DEVELOPER_URL, publish.developer.url)
        scalar(LvmKeys.PUBLISH_SCM_URL, publish.scm.url)
        scalar(LvmKeys.PUBLISH_SCM_CONNECTION, publish.scm.connection)
        scalar(LvmKeys.PUBLISH_SCM_DEVELOPER_CONNECTION, publish.scm.developerConnection)
        scalar(LvmKeys.PUBLISH_SIGNING, publish.signing)
        scalar(LvmKeys.PUBLISH_ABI_ENABLED, publish.abi.enabled)
        list(LvmKeys.PUBLISH_ABI_EXCLUDED_NAMES, publish.abi.excludedNames)
        list(LvmKeys.PUBLISH_ABI_EXCLUDED_ANNOTATIONS, publish.abi.excludedAnnotations)
        scalar(LvmKeys.APPLICATION_ID, application.applicationId)
        scalar(LvmKeys.APPLICATION_VERSION_CODE, application.versionCode)
        scalar(LvmKeys.APPLICATION_VERSION_NAME, application.versionName)
        scalar(LvmKeys.APPLICATION_MINIFY, application.minify)
        list(LvmKeys.APPLICATION_PROGUARD_FILES, application.proguardFiles)
        scalar(LvmKeys.APPLICATION_DESUGARING, application.desugaring)
        scalar(LvmKeys.LIBRARY_DESUGARING, library.desugaring)
        list(LvmKeys.LIBRARY_CONSUMER_PROGUARD_FILES, library.consumerProguardFiles)
        scalar(LvmKeys.LIBRARY_RESOURCE_PREFIX, library.resourcePrefix)
        list(LvmKeys.LIBRARY_RESOURCE_PREFIX_DROPPED_SEGMENTS, library.resourcePrefixDroppedSegments)
        scalar(LvmKeys.LIBRARY_SKIP_EMPTY_ANDROID_TEST, library.skipEmptyAndroidTest)
        return result
    }
}

/**
 * A Gradle list starts as an empty list, which can't be told apart from one set to `emptyList()`. Without a value, a
 * list that the settings script leaves alone is not forwarded, while an empty one is, and replaces the default.
 */
private fun startWithoutValue(vararg lists: ListProperty<String>) = lists.forEach { it.unsetConvention() }

abstract class AndroidDefaults {
    abstract val compileSdk: Property<Int>
    abstract val minSdk: Property<Int>
    abstract val targetSdk: Property<Int>
    abstract val testInstrumentationRunner: Property<String>
    abstract val hostTests: Property<Boolean>
}

abstract class JvmDefaults {
    abstract val target: Property<Int>
}

abstract class KotlinDefaults {
    abstract val warningsAsErrors: Property<Boolean>
    abstract val optIns: ListProperty<String>
    abstract val freeCompilerArgs: ListProperty<String>
    abstract val explicitApi: Property<Boolean>
    abstract val testDependencies: Property<Boolean>
    abstract val flatLayout: Property<Boolean>

    /** `enable`, `no-compatibility` or `disable`, as Kotlin's `-jvm-default`. */
    abstract val jvmDefault: Property<String>

    init {
        startWithoutValue(optIns, freeCompilerArgs)
    }
}

abstract class IosDefaults {
    /** `iosArm64`, `iosSimulatorArm64`. */
    abstract val architectures: ListProperty<String>

    init {
        startWithoutValue(architectures)
    }
}

abstract class QualityDefaults {
    /** detekt config files, relative to the root project directory. */
    abstract val config: ListProperty<String>

    /** Directories to analyze, relative to each module. */
    abstract val source: ListProperty<String>
    abstract val buildUponDefaultConfig: Property<Boolean>
    abstract val composeRules: Property<Boolean>

    init {
        startWithoutValue(config, source)
    }
}

abstract class MainContextDefaults {
    /** `android`, `ios`, `jvm`. */
    abstract val platforms: ListProperty<String>
    abstract val dependency: Property<Boolean>

    init {
        startWithoutValue(platforms)
    }
}

abstract class ComposeDefaults {
    abstract val metrics: Property<Boolean>
    abstract val reports: Property<Boolean>

    /** Stability configuration files of the Compose compiler, relative to the root project directory. */
    abstract val stabilityConfigurationFiles: ListProperty<String>

    init {
        startWithoutValue(stabilityConfigurationFiles)
    }
}

abstract class PublishDefaults @Inject constructor(objects: ObjectFactory) {
    /** Put before the project name to make the artifact ID, such as `lvm-` for `lvm-core`. */
    abstract val artifactIdPrefix: Property<String>
    abstract val description: Property<String>
    abstract val url: Property<String>
    abstract val signing: Property<Boolean>

    val license: PomLicenseDefaults = objects.newInstance(PomLicenseDefaults::class.java)
    val developer: PomDeveloperDefaults = objects.newInstance(PomDeveloperDefaults::class.java)
    val scm: PomScmDefaults = objects.newInstance(PomScmDefaults::class.java)
    val abi: AbiDefaults = objects.newInstance(AbiDefaults::class.java)

    fun license(action: Action<PomLicenseDefaults>) = action.execute(license)
    fun developer(action: Action<PomDeveloperDefaults>) = action.execute(developer)
    fun scm(action: Action<PomScmDefaults>) = action.execute(scm)
    fun abi(action: Action<AbiDefaults>) = action.execute(abi)
}

abstract class PomLicenseDefaults {
    abstract val name: Property<String>
    abstract val url: Property<String>
}

abstract class PomDeveloperDefaults {
    abstract val id: Property<String>
    abstract val name: Property<String>
    abstract val url: Property<String>
}

abstract class PomScmDefaults {
    abstract val url: Property<String>
    abstract val connection: Property<String>
    abstract val developerConnection: Property<String>
}

abstract class AbiDefaults {
    abstract val enabled: Property<Boolean>

    /** Classes and members to leave out of the ABI dump, by name. */
    abstract val excludedNames: ListProperty<String>

    /** Annotations whose declarations stay out of the ABI dump. */
    abstract val excludedAnnotations: ListProperty<String>

    init {
        startWithoutValue(excludedNames, excludedAnnotations)
    }
}

abstract class ApplicationDefaults {
    abstract val applicationId: Property<String>
    abstract val versionCode: Property<Int>
    abstract val versionName: Property<String>
    abstract val minify: Property<Boolean>

    /** Module-relative paths, before a module's own. */
    abstract val proguardFiles: ListProperty<String>
    abstract val desugaring: Property<Boolean>

    init {
        startWithoutValue(proguardFiles)
    }
}

abstract class LibraryDefaults {
    abstract val desugaring: Property<Boolean>

    /** Module-relative paths, before a module's own. */
    abstract val consumerProguardFiles: ListProperty<String>
    abstract val resourcePrefix: Property<Boolean>

    /** Segments that the resource prefix leaves out at the end of the module path, such as `ui`. */
    abstract val resourcePrefixDroppedSegments: ListProperty<String>
    abstract val skipEmptyAndroidTest: Property<Boolean>

    init {
        startWithoutValue(consumerProguardFiles, resourcePrefixDroppedSegments)
    }
}
