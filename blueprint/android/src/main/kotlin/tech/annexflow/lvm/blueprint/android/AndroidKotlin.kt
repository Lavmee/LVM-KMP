package tech.annexflow.lvm.blueprint.android

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.KotlinValues
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.reportTool

/**
 * Compiler options of the Kotlin that AGP builds in, and kotlin-test for unit tests. The JVM target follows the Java
 * version, as AGP derives it, so it isn't set here.
 *
 * Unit tests run on JUnit 4, so they get kotlin-test-junit: kotlin-test alone has no `@Test` for the JVM.
 */
internal fun Project.configureKotlin(section: KotlinSection, values: KotlinValues) {
    reportTool("kotlin", getKotlinPluginVersion(), LvmBuildInfo.MIN_KOTLIN, LvmBuildInfo.TESTED_KOTLIN)
    val optIns = values.optIns.zip(section.optIns) { buildWide, module -> buildWide + module }
    val freeArgs = values.freeCompilerArgs.zip(section.freeCompilerArgs) { buildWide, module -> buildWide + module }
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

/**
 * Strict explicit API mode for the main code of every variant. A variant's main compilation carries the variant's name;
 * those of unit tests, instrumented tests and test fixtures don't, so tests keep default visibility.
 */
internal fun Project.configureExplicitApi(section: KotlinSection, androidComponents: AndroidComponentsExtension<*, *, *>) {
    val arguments = section.explicitApi.map { enabled -> if (enabled) listOf("-Xexplicit-api=strict") else emptyList() }
    val compilations = extensions.getByType(KotlinAndroidProjectExtension::class.java).target.compilations
    androidComponents.onVariants { variant ->
        compilations.matching { it.name == variant.name }.configureEach {
            compileTaskProvider.configure { compilerOptions.freeCompilerArgs.addAll(arguments) }
        }
    }
}
