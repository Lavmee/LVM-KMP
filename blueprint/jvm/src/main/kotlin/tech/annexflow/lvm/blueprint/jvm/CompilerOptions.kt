package tech.annexflow.lvm.blueprint.jvm

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.KotlinValues
import tech.annexflow.lvm.blueprint.common.lvmValues

/**
 * Sets the Java version and Kotlin's JVM target. As in Android modules, the build-wide `jvm.target` goes into `java { }`
 * when the plugin is applied, so `sourceCompatibility` and `targetCompatibility` in the build script replace it, and a
 * module's own `lvm { jvm { target } }` applies over both.
 *
 * Kotlin's `jvmTarget` follows that Java version, and Java compiles with `--release` of Kotlin's `jvmTarget`, so a
 * native `kotlin { compilerOptions { jvmTarget } }` moves both. Both are conventions, so native values win.
 */
internal fun Project.configureJavaVersion(jvm: JvmSection, kotlin: KotlinJvmProjectExtension) {
    val java = extensions.getByType(JavaPluginExtension::class.java)
    val buildWide = lvmValues.jvm.target.get()
    java.sourceCompatibility = JavaVersion.toVersion(buildWide)
    java.targetCompatibility = JavaVersion.toVersion(buildWide)

    // The section's convention is the build-wide value, so a different one is the module's.
    val javaVersion = jvm.target.map { target ->
        if (target != buildWide) target else java.targetCompatibility.majorVersion.toInt()
    }
    kotlin.compilerOptions.jvmTarget.convention(javaVersion.map(::kotlinJvmTarget))
    val release = kotlin.compilerOptions.jvmTarget.map { it.target.removePrefix("1.").toInt() }
    tasks.withType(JavaCompile::class.java).configureEach { options.release.convention(release) }
}

/** Kotlin names Java 8 `1.8` and every later version by its number. */
private fun kotlinJvmTarget(javaVersion: Int): JvmTarget =
    JvmTarget.fromTarget(if (javaVersion == JAVA_8) "1.8" else javaVersion.toString())

private const val JAVA_8 = 8

/** Passes `lvm { kotlin { } }` to every compilation, and strict explicit API mode to the main one: tests keep default visibility. */
internal fun configureCompilerOptions(kotlin: KotlinJvmProjectExtension, section: KotlinSection, values: KotlinValues) {
    val optIns = values.optIns.zip(section.optIns) { buildWide, module -> buildWide + module }
    val freeArgs = values.freeCompilerArgs.zip(section.freeCompilerArgs) { buildWide, module -> buildWide + module }
    kotlin.compilerOptions {
        allWarningsAsErrors.set(section.warningsAsErrors)
        optIn.addAll(optIns)
        freeCompilerArgs.addAll(freeArgs)
    }

    val explicitApi = section.explicitApi.map { enabled -> if (enabled) listOf("-Xexplicit-api=strict") else emptyList() }
    kotlin.target.compilations.matching { it.name == KotlinCompilation.MAIN_COMPILATION_NAME }.configureEach {
        compileTaskProvider.configure { compilerOptions.freeCompilerArgs.addAll(explicitApi) }
    }
}

/**
 * Adds kotlin-test to unit tests. Kotlin picks its variant for the framework of the `test` task: kotlin-test-junit for
 * JUnit 4, or kotlin-test-junit5 once the module calls `useJUnitPlatform()`.
 *
 * Tests stay on JUnit 4, Gradle's default and the framework of Android unit tests: kotlin-test-junit brings junit, which
 * is all Gradle needs to run them. The JUnit Platform also needs junit-platform-launcher at the version of the module's
 * JUnit engine, which only the module can choose.
 */
internal fun Project.configureTestDependencies(section: KotlinSection) {
    val kotlinTest = dependencies.create("org.jetbrains.kotlin:kotlin-test:${getKotlinPluginVersion()}")
    val kotlinTestIfEnabled = section.testDependencies.map { enabled -> if (enabled) listOf(kotlinTest) else emptyList() }
    configurations.named("testImplementation").configure { dependencies.addAllLater(kotlinTestIfEnabled) }
}
