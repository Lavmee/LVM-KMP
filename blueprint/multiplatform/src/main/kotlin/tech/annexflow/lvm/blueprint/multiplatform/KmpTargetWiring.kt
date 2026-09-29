package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues

internal class KmpTargetWiring(
    private val project: Project,
    private val kotlin: KotlinMultiplatformExtension,
    private val androidSection: AndroidSection,
    private val jvmSection: JvmSection,
    values: LvmValues,
) : TargetWiring {

    override val defaultHostTests: Provider<Boolean> =
        values.boolean(LvmKeys.ANDROID_HOST_TESTS, LvmDefaults.ANDROID_HOST_TESTS)

    override val defaultIosArchitectures: Provider<List<String>> =
        values.list(LvmKeys.IOS_ARCHITECTURES, LvmDefaults.IOS_ARCHITECTURES)

    // configureAndroidTarget lives in AndroidTarget.kt, which is the only class that touches AGP.
    override fun android(spec: AndroidTargetSpec) = project.configureAndroidTarget(kotlin, androidSection, jvmSection, spec)

    override fun ios(spec: IosTargetSpec) = kotlin.configureIosTargets(spec)

    override fun jvm() {
        kotlin.jvm()
    }
}
