package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.InvalidUserDataException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun KotlinMultiplatformExtension.configureIosTargets(spec: IosTargetSpec) {
    throw InvalidUserDataException("iOS targets are not supported yet.")
}
