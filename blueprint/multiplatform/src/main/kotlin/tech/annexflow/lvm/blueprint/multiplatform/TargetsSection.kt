package tech.annexflow.lvm.blueprint.multiplatform

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/** `lvm { targets { } }`: each call creates its targets immediately, using the spec configured in the call. */
abstract class TargetsSection @Inject internal constructor(
    private val objects: ObjectFactory,
    private val wiring: TargetWiring,
) {
    fun android() = android { }

    fun android(action: Action<AndroidTargetSpec>) {
        val spec = objects.newInstance(AndroidTargetSpec::class.java)
        spec.hostTests.convention(wiring.defaultHostTests)
        action.execute(spec)
        wiring.android(spec)
    }

    fun ios() = ios { }

    fun ios(action: Action<IosTargetSpec>) {
        val spec = objects.newInstance(IosTargetSpec::class.java)
        spec.architectures.convention(wiring.defaultIosArchitectures)
        action.execute(spec)
        wiring.ios(spec)
    }

    fun jvm() = wiring.jvm()
}

abstract class AndroidTargetSpec {
    /** Creates the Android host test compilation. Build-wide key: `lvm.android.hostTests`. */
    abstract val hostTests: Property<Boolean>
}

abstract class IosTargetSpec @Inject constructor(private val objects: ObjectFactory) {
    /** Any of `iosArm64`, `iosSimulatorArm64`. Build-wide key: `lvm.ios.architectures`. Replace it with `set(...)`. */
    abstract val architectures: ListProperty<String>

    internal var frameworkSpec: FrameworkSpec? = null
        private set

    /** Adds a framework binary to every iOS target. */
    fun framework(action: Action<FrameworkSpec>) {
        val spec = frameworkSpec ?: objects.newInstance(FrameworkSpec::class.java).also {
            it.isStatic.convention(false)
            frameworkSpec = it
        }
        action.execute(spec)
    }
}

abstract class FrameworkSpec {
    abstract val baseName: Property<String>
    abstract val isStatic: Property<Boolean>

    /** Passed to Kotlin/Native as the `bundleId` binary option; without it the compiler infers one and may warn. */
    abstract val bundleId: Property<String>
}

internal interface TargetWiring {
    val defaultHostTests: Provider<Boolean>
    val defaultIosArchitectures: Provider<List<String>>
    fun android(spec: AndroidTargetSpec)
    fun ios(spec: IosTargetSpec)
    fun jvm()
}
