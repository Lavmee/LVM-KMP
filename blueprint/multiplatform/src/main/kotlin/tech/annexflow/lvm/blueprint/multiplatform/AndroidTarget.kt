package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection

internal fun Project.configureAndroidTarget(kotlin: KotlinMultiplatformExtension, section: AndroidSection, spec: AndroidTargetSpec) {
    throw InvalidUserDataException("The Android target is not supported yet.")
}
