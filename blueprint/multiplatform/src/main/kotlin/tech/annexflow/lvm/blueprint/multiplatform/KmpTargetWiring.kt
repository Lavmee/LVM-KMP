package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues
import tech.annexflow.lvm.blueprint.common.lvmReport
import tech.annexflow.lvm.blueprint.common.lvmValues

internal class KmpTargetWiring(
    private val project: Project,
    private val kotlin: KotlinMultiplatformExtension,
    private val androidSection: AndroidSection,
    private val jvmSection: JvmSection,
    private val flatLayout: Provider<Boolean>,
    private val values: LvmValues,
) : TargetWiring {

    override val defaultHostTests: Provider<Boolean> = project.lvmValues.android.hostTests

    override val defaultIosArchitectures: Provider<List<String>> =
        values.list(LvmKeys.IOS_ARCHITECTURES, LvmDefaults.IOS_ARCHITECTURES)

    // configureAndroidTarget lives in AndroidTarget.kt, which is the only class that touches AGP.
    override fun android(spec: AndroidTargetSpec) {
        values.report(LvmKeys.ANDROID_HOST_TESTS, spec.hostTests, defaultHostTests)
        val details = spec.hostTests.map { hostTests -> mapOf("hostTests" to hostTests) }
        project.lvmReport { targets.put("android", details) }
        project.configureAndroidTarget(kotlin, androidSection, jvmSection, flatLayout, spec)
    }

    override fun ios(spec: IosTargetSpec) {
        values.report(LvmKeys.IOS_ARCHITECTURES, spec.architectures, defaultIosArchitectures)
        val details = linkedMapOf<String, Any>("architectures" to spec.architectures.get())
        spec.frameworkSpec?.let { framework ->
            details["framework"] = listOfNotNull(
                framework.baseName.orNull?.let { "baseName" to it },
                "isStatic" to framework.isStatic.get(),
                framework.bundleId.orNull?.let { "bundleId" to it },
            ).toMap(LinkedHashMap<String, Any>())
        }
        project.lvmReport { targets.put("ios", details) }
        kotlin.configureIosTargets(spec)
    }

    override fun jvm() {
        project.lvmReport { targets.put("jvm", emptyMap<String, Any>()) }
        kotlin.jvm()
    }
}
