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

    fun android(action: Action<AndroidDefaults>) = action.execute(android)
    fun jvm(action: Action<JvmDefaults>) = action.execute(jvm)
    fun kotlin(action: Action<KotlinDefaults>) = action.execute(kotlin)
    fun ios(action: Action<IosDefaults>) = action.execute(ios)
    fun quality(action: Action<QualityDefaults>) = action.execute(quality)

    /** Every value that was set, as `lvm.<key>` → string. Empty lists are not forwarded. */
    internal fun forwardedValues(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        values.get().forEach { (key, value) -> result[LvmKeys.property(key)] = value }

        fun scalar(key: String, value: Provider<*>) {
            value.orNull?.let { result[LvmKeys.property(key)] = it.toString() }
        }

        fun list(key: String, value: ListProperty<String>) {
            value.get().takeIf(List<String>::isNotEmpty)?.let { result[LvmKeys.property(key)] = LvmValueParsing.joinList(it) }
        }

        scalar(LvmKeys.ANDROID_COMPILE_SDK, android.compileSdk)
        scalar(LvmKeys.ANDROID_MIN_SDK, android.minSdk)
        scalar(LvmKeys.ANDROID_HOST_TESTS, android.hostTests)
        scalar(LvmKeys.JVM_TARGET, jvm.target)
        scalar(LvmKeys.KOTLIN_WARNINGS_AS_ERRORS, kotlin.warningsAsErrors)
        list(LvmKeys.KOTLIN_OPT_INS, kotlin.optIns)
        list(LvmKeys.KOTLIN_FREE_COMPILER_ARGS, kotlin.freeCompilerArgs)
        scalar(LvmKeys.KOTLIN_EXPLICIT_API, kotlin.explicitApi)
        scalar(LvmKeys.KOTLIN_TEST_DEPENDENCIES, kotlin.testDependencies)
        list(LvmKeys.IOS_ARCHITECTURES, ios.architectures)
        list(LvmKeys.QUALITY_CONFIG, quality.config)
        list(LvmKeys.QUALITY_SOURCE, quality.source)
        scalar(LvmKeys.QUALITY_BUILD_UPON_DEFAULT_CONFIG, quality.buildUponDefaultConfig)
        scalar(LvmKeys.QUALITY_COMPOSE_RULES, quality.composeRules)
        return result
    }
}

abstract class AndroidDefaults {
    abstract val compileSdk: Property<Int>
    abstract val minSdk: Property<Int>
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
}

abstract class IosDefaults {
    /** `iosArm64`, `iosSimulatorArm64`. */
    abstract val architectures: ListProperty<String>
}

abstract class QualityDefaults {
    /** detekt config files, relative to the root project directory. */
    abstract val config: ListProperty<String>

    /** Directories to analyze, relative to each module. */
    abstract val source: ListProperty<String>
    abstract val buildUponDefaultConfig: Property<Boolean>
    abstract val composeRules: Property<Boolean>
}
