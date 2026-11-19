package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import tech.annexflow.lvm.blueprint.common.AndroidSection
import tech.annexflow.lvm.blueprint.common.JvmSection
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo
import tech.annexflow.lvm.blueprint.common.lvmReport
import tech.annexflow.lvm.blueprint.common.reportAndroidValues
import tech.annexflow.lvm.blueprint.common.reportPlugin
import tech.annexflow.lvm.blueprint.common.reportTool
import tech.annexflow.lvm.blueprint.common.requireSupported

/** Fails below the oldest AGP that LVM supports, and warns above the one it was tested with. */
internal fun Project.requireSupportedAgp(androidComponents: AndroidComponentsExtension<*, *, *>) {
    val agp = androidComponents.pluginVersion
    val agpVersion = "${agp.major}.${agp.minor}.${agp.micro}"
    requireSupported(
        tool = "Android Gradle plugin",
        actual = agpVersion,
        minimum = LvmBuildInfo.MIN_AGP,
        tested = LvmBuildInfo.TESTED_AGP,
    )
    reportTool("agp", agpVersion, LvmBuildInfo.MIN_AGP, LvmBuildInfo.TESTED_AGP)
}

/** Adds the plugin [pluginId], the values of `lvm { android { } }` and the source sets of [extension] to the module's report. */
internal fun Project.reportAndroidModule(pluginId: String, android: AndroidSection, extension: CommonExtension) {
    reportPlugin(pluginId)
    reportAndroidValues(android)
    val sourceSets = provider { extension.sourceSets.names.sorted() }
    lvmReport { fields.put("sourceSets", sourceSets) }
}

/** Fills in the compile and minimum SDK levels and the namespace where the build script left them unset. */
internal fun CommonExtension.fillSdkAndNamespace(android: AndroidSection) {
    compileSdk { fillCompileSdk(android) }
    // With a preview level, AGP reports an API level for minSdk, so it isn't null either.
    if (defaultConfig.minSdk == null) defaultConfig.minSdk = android.minSdk.get()
    if (namespace == null) namespace = android.namespace.get()
}

/**
 * Sets the compile SDK level of [android], with its minor level when there is one. The spec starts with the version the
 * build script set, so a level, a minor level, a preview or an add-on set there stays.
 */
private fun CompileSdkSpec.fillCompileSdk(android: AndroidSection) {
    if (version != null) return
    val level = android.compileSdk.get()
    val minor = android.compileSdkMinor.orNull
    version = if (minor == null) release(level) else release(level) { minorApiLevel = minor }
}

internal fun CommonExtension.fillTestInstrumentationRunner(android: AndroidSection) {
    val config = defaultConfig
    if (config.testInstrumentationRunner == null) config.testInstrumentationRunner = android.testInstrumentationRunner.get()
}

/**
 * Sets the Java version, which AGP's built-in Kotlin follows for its JVM target. AGP throws when finalizeDsl reads the
 * Java version, set or not, so lvm can't tell a native one there: the plugins set the build-wide version when they are
 * applied, so `compileOptions` in the build script replace it, and [applyModuleJvmTarget] applies a module's own value.
 */
internal fun CommonExtension.setJavaVersion(version: Int) {
    compileOptions.sourceCompatibility = JavaVersion.toVersion(version)
    compileOptions.targetCompatibility = JavaVersion.toVersion(version)
}

/** Applies a module's own `lvm { jvm { target } }`. The section's convention is the build-wide value, so a different one is the module's. */
internal fun CommonExtension.applyModuleJvmTarget(jvm: JvmSection, buildWideJvmTarget: Int) {
    val jvmTarget = jvm.target.get()
    if (jvmTarget != buildWideJvmTarget) setJavaVersion(jvmTarget)
}

/**
 * Leaves metadata that several jars carry under the same name, and that no app needs at runtime, out of the APK, or
 * out of the APK of a library's instrumented tests. A build script removes an entry with
 * `android { packaging.resources.excludes -= "..." }`.
 */
internal fun CommonExtension.excludeDuplicateMetadata() {
    packaging.resources.excludes.addAll(listOf("/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES"))
}

/** Turns core library desugaring on while [enabled]. It never turns it off, so a build script that turns it on keeps it. */
internal fun CommonExtension.enableDesugaring(enabled: Boolean) {
    if (enabled) compileOptions.isCoreLibraryDesugaringEnabled = true
}

/** Adds `desugar_jdk_libs` to `coreLibraryDesugaring` while [enabled] is true. */
internal fun Project.addDesugaringDependency(enabled: Provider<Boolean>) {
    val desugarJdkLibs = dependencies.create(LvmBuildInfo.DESUGAR_JDK_LIBS)
    val dependencyIfEnabled = enabled.map { on -> if (on) listOf(desugarJdkLibs) else emptyList() }
    configurations.named("coreLibraryDesugaring").configure { dependencies.addAllLater(dependencyIfEnabled) }
}
