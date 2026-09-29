package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.LvmDefaults
import tech.annexflow.lvm.blueprint.common.LvmKeys
import tech.annexflow.lvm.blueprint.common.LvmValues

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun configureKotlinCompilerOptions(kotlin: KotlinMultiplatformExtension, section: KotlinSection, values: LvmValues) {
    val optIns = values.list(LvmKeys.KOTLIN_OPT_INS, LvmDefaults.KOTLIN_OPT_INS)
        .zip(section.optIns) { buildWide, module -> buildWide + module }
    val freeArgs = values.list(LvmKeys.KOTLIN_FREE_COMPILER_ARGS, LvmDefaults.KOTLIN_FREE_COMPILER_ARGS)
        .zip(section.freeCompilerArgs) { buildWide, module -> buildWide + module }

    kotlin.compilerOptions {
        allWarningsAsErrors.set(section.warningsAsErrors)
        optIn.addAll(optIns)
        freeCompilerArgs.addAll(freeArgs)
    }

    // Explicit API mode covers main code only; tests keep default visibility.
    val explicitApiArguments = section.explicitApi.map { enabled -> if (enabled) listOf("-Xexplicit-api=strict") else emptyList() }
    kotlin.targets.configureEach {
        compilations.configureEach {
            if (name == KotlinCompilation.MAIN_COMPILATION_NAME) {
                compileTaskProvider.configure {
                    compilerOptions.freeCompilerArgs.addAll(explicitApiArguments)
                }
            }
        }
    }
}

/** Sets jvmTarget on every JVM and Android compilation; the KMP top-level options can't, because they are common options. */
internal fun Project.configureJvmTarget(section: JvmSection) {
    val jvmTarget = section.target.map { JvmTarget.fromTarget(it.toString()) }
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        compilerOptions.jvmTarget.set(jvmTarget)
    }
}

internal fun Project.configureTestDependencies(section: KotlinSection) {
    val kotlinTest = dependencies.create("org.jetbrains.kotlin:kotlin-test:${getKotlinPluginVersion()}")
    val kotlinTestIfEnabled = section.testDependencies.map { enabled -> if (enabled) listOf(kotlinTest) else emptyList() }
    // Added lazily rather than in withDependencies: KGP picks kotlin-test's JVM framework variant (kotlin.test.Test) in its
    // own withDependencies action on the JVM test configurations, which runs before those of commonTestImplementation.
    configurations.named("commonTestImplementation").configure {
        dependencies.addAllLater(kotlinTestIfEnabled)
    }
}
