package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.InvalidUserDataException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun KotlinMultiplatformExtension.configureIosTargets(spec: IosTargetSpec) {
    val framework = spec.frameworkSpec
    for (architecture in spec.architectures.get()) {
        val target = when (architecture) {
            "iosArm64" -> iosArm64()
            "iosSimulatorArm64" -> iosSimulatorArm64()
            else -> throw InvalidUserDataException(
                "Unknown iOS architecture '$architecture'. Supported: iosArm64, iosSimulatorArm64.",
            )
        }
        if (framework != null) {
            val baseName = framework.baseName.orNull
                ?: throw InvalidUserDataException("Set lvm { targets { ios { framework { baseName = \"...\" } } } }.")
            val isStatic = framework.isStatic.get()
            val bundleId = framework.bundleId.orNull
            target.binaries.framework {
                this.baseName = baseName
                this.isStatic = isStatic
                if (bundleId != null) binaryOption("bundleId", bundleId)
            }
        }
    }
}
