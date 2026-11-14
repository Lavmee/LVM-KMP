package tech.annexflow.lvm.blueprint.android

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues

/**
 * Compiler options of the Kotlin that AGP builds in, and kotlin-test for unit tests. The JVM target follows the Java
 * version, as AGP derives it, so it isn't set here.
 *
 * Unit tests run on JUnit 4, so they get kotlin-test-junit: kotlin-test alone has no `@Test` for the JVM.
 */
internal fun Project.configureKotlin(section: KotlinSection, values: LvmValues) {
    val optIns = values.list(LvmKeys.KOTLIN_OPT_INS, LvmDefaults.KOTLIN_OPT_INS)
        .zip(section.optIns) { buildWide, module -> buildWide + module }
    val freeArgs = values.list(LvmKeys.KOTLIN_FREE_COMPILER_ARGS, LvmDefaults.KOTLIN_FREE_COMPILER_ARGS)
        .zip(section.freeCompilerArgs) { buildWide, module -> buildWide + module }
    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            allWarningsAsErrors.set(section.warningsAsErrors)
            optIn.addAll(optIns)
            freeCompilerArgs.addAll(freeArgs)
        }
    }

    val kotlinTest = dependencies.create("org.jetbrains.kotlin:kotlin-test-junit:${getKotlinPluginVersion()}")
    val kotlinTestIfEnabled = section.testDependencies.map { enabled -> if (enabled) listOf(kotlinTest) else emptyList() }
    configurations.named("testImplementation").configure { dependencies.addAllLater(kotlinTestIfEnabled) }
}
