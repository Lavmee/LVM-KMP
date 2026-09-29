package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
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

/**
 * Makes the lvm JVM target the convention of every JVM target's compiler options, which KGP passes on to the target's
 * compile tasks, and of the Java compile tasks of those targets. The Android target gets the same convention in
 * AndroidTarget.kt. The KMP top-level options can't carry jvmTarget, because they are common options.
 *
 * Being conventions, they lose to a value set with native DSL, such as `kotlin { jvm { compilerOptions { jvmTarget = … } } }`.
 */
internal fun configureJvmTarget(kotlin: KotlinMultiplatformExtension, section: JvmSection) {
    val jvmTarget = section.kotlinJvmTarget()
    kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach {
        compilerOptions.jvmTarget.convention(jvmTarget)

        // Java sources would otherwise keep the JDK's target, which KGP rejects when it differs from Kotlin's.
        // They follow the target's jvmTarget, so a native value applies to them too.
        val release = compilerOptions.jvmTarget.map { it.target.removePrefix("1.").toInt() }
        compilations.configureEach {
            compileJavaTaskProvider?.configure { options.release.convention(release) }
        }
    }
}

internal fun JvmSection.kotlinJvmTarget(): Provider<JvmTarget> = target.map { JvmTarget.fromTarget(it.toString()) }

internal fun Project.configureTestDependencies(section: KotlinSection) {
    val kotlinTest = dependencies.create("org.jetbrains.kotlin:kotlin-test:${getKotlinPluginVersion()}")
    val kotlinTestIfEnabled = section.testDependencies.map { enabled -> if (enabled) listOf(kotlinTest) else emptyList() }
    // Added lazily rather than in withDependencies: KGP picks kotlin-test's JVM framework variant (kotlin.test.Test) in its
    // own withDependencies action on the JVM test configurations, which runs before those of commonTestImplementation.
    configurations.named("commonTestImplementation").configure {
        dependencies.addAllLater(kotlinTestIfEnabled)
    }
}
