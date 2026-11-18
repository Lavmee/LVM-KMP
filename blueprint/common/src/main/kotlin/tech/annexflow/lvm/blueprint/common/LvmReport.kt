package tech.annexflow.lvm.blueprint.common

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion
import org.gradle.work.DisableCachingByDefault

/**
 * Writes what LVM's plugins set up in a module as JSON, for tools such as lvm-tui: the LVM plugins, the targets, every
 * value with where it comes from, the versions of Gradle, Kotlin and AGP, the declared dependencies and the tasks.
 *
 * Every module with an LVM plugin has one, named [LVM_REPORT_TASK]. It writes `build/lvm/report/<path>/project.json`
 * of the root project, where `<path>` is the module's path with `/` for `:`, so a tool runs `lvmReport` once and reads
 * the reports of the whole build from one directory. [SCHEMA] changes when a field changes its meaning.
 */
@InternalBlueprintApi
@DisableCachingByDefault(because = "Writes a small file from its inputs.")
abstract class LvmReportTask : DefaultTask() {

    /** Top-level fields that a single plugin sets, such as `sourceSets` or `publication`. */
    @get:Input
    abstract val fields: MapProperty<String, Any>

    /** IDs of the LVM plugins that the module applies. */
    @get:Input
    abstract val plugins: SetProperty<String>

    /** Targets of a Kotlin Multiplatform module by name, each with its settings. */
    @get:Input
    abstract val targets: MapProperty<String, Any>

    /** Versions of Gradle, Kotlin and AGP: the one the build uses, the oldest LVM supports and the one LVM was tested with. */
    @get:Input
    abstract val toolchain: MapProperty<String, Any>

    /**
     * Values by key, each with the value the plugins use and its source: `default`, `settings` for the settings `lvm { }`,
     * `property` for a `lvm.*` Gradle property, or `module` for the module's own `lvm { }`.
     */
    @get:Input
    abstract val values: MapProperty<String, Any>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun write() {
        val fields = fields.get()
        val document = linkedMapOf<String, Any?>("schema" to SCHEMA)
        LEADING_FIELDS.forEach { name -> fields[name]?.let { document[name] = it } }
        document["plugins"] = plugins.get().sorted()
        document["targets"] = targets.get().toSortedMap()
        document["toolchain"] = toolchain.get().toSortedMap()
        document["values"] = values.get().toSortedMap()
        (fields.keys - document.keys).sorted().forEach { name -> document[name] = fields.getValue(name) }
        destination.get().asFile.writeText(LvmJson.write(document))
    }
}

/** Version of the report's format. */
private const val SCHEMA = 1

private val LEADING_FIELDS = listOf("lvmVersion", "path", "projectDir")

/** Name of the report task. */
@InternalBlueprintApi
const val LVM_REPORT_TASK: String = "lvmReport"

/** Prefix of the settings plugin's own options, which it passes to every project as extra properties for the report. */
@InternalBlueprintApi
const val LVM_SETTINGS_PREFIX: String = "lvm.settings."

/**
 * Configures the module's [LvmReportTask], registering it the first time. A task of another type named `lvmReport`,
 * which a build may have of its own, is left alone.
 */
@InternalBlueprintApi
fun Project.lvmReport(action: Action<LvmReportTask>) {
    if (LVM_REPORT_TASK !in tasks.names) registerLvmReport()
    tasks.withType(LvmReportTask::class.java).named { it == LVM_REPORT_TASK }.configureEach(action)
}

/** Adds the plugin [id] to the module's report. */
@InternalBlueprintApi
fun Project.reportPlugin(id: String) {
    lvmReport { plugins.add(id) }
}

/** Adds the versions of [tool], such as `kotlin` or `agp`, to the module's report. */
@InternalBlueprintApi
fun Project.reportTool(tool: String, actual: String, minimum: String, tested: String) {
    lvmReport { toolchain.put(tool, toolVersions(actual, minimum, tested)) }
}

/**
 * Adds a value that only a module sets, such as `android.namespace`, to the module's report. [default] is the value
 * the module gets without setting one.
 */
@InternalBlueprintApi
fun <T : Any> Project.reportModuleValue(name: String, effective: Provider<T>, default: Provider<T>) {
    reportValue(name, effective, default, provider { DEFAULT_SOURCE })
}

/**
 * Adds the value of [key] to the module's report. [effective] is the value the plugins use and [buildWide] the one the
 * module gets without its own `lvm { }`, which comes from [source]. A value that differs from [buildWide] is the module's.
 */
internal fun <T : Any> Project.reportValue(key: String, effective: Provider<T>, buildWide: Provider<T>, source: Provider<String>) {
    val entry = effective.asOptional().zip(buildWide.asOptional()) { value, default -> value to default }
        .zip(source) { (value, default), layer ->
            linkedMapOf("value" to value.firstOrNull(), "source" to if (value == default) layer else MODULE_SOURCE)
        }
    lvmReport { values.put(key, entry) }
}

/** A provider that is always present: a list with the value, or an empty list while there is none. */
private fun <T : Any> Provider<T>.asOptional(): Provider<List<Any>> = map<List<Any>> { listOf(it) }.orElse(emptyList())

private fun Project.registerLvmReport() {
    @Suppress("UnstableApiUsage") // Project.isolated: reads only the root directory, safe under Isolated Projects.
    val rootDirectory = isolated.rootProject.projectDirectory
    val segments = path.split(':').filter(String::isNotEmpty)
    val destination = rootDirectory.file((REPORT_DIRECTORY + segments + "project.json").joinToString("/"))
    val modulePath = path
    val moduleDirectory = projectDir.relativeTo(rootDirectory.asFile).invariantSeparatorsPath
    val settingsOptions = settingsOptions()

    // Only configurations that something realized: those are where dependencies are declared, and realizing the
    // others late would run plugin code at an unexpected time.
    val realizedConfigurations = mutableListOf<Configuration>()
    configurations.configureEach { realizedConfigurations += this }
    val projectDependencies = provider { realizedConfigurations.declared { (it as? ProjectDependency)?.path } }
    val lvmDependencies = provider {
        realizedConfigurations.declared { dependency ->
            dependency.name.takeIf { dependency is ExternalModuleDependency && dependency.group == LvmBuildInfo.GROUP }
        }
    }
    val taskNames = provider { tasks.names.sorted() }

    tasks.register(LVM_REPORT_TASK, LvmReportTask::class.java) {
        group = "help"
        description = "Writes what LVM's plugins set up in this module as JSON, for tools such as lvm-tui."
        this.destination.set(destination)
        fields.put("lvmVersion", LvmBuildInfo.VERSION)
        fields.put("path", modulePath)
        fields.put("projectDir", moduleDirectory)
        if (settingsOptions.isNotEmpty()) fields.put("settings", settingsOptions)
        fields.put("projectDependencies", projectDependencies)
        fields.put("lvmDependencies", lvmDependencies)
        fields.put("tasks", taskNames)
        toolchain.put("gradle", toolVersions(GradleVersion.current().version, LvmBuildInfo.MIN_GRADLE, LvmBuildInfo.TESTED_GRADLE))
    }
}

/** The settings plugin's options, which it passes as `lvm.settings.<name>` extra properties. Empty without it. */
private fun Project.settingsOptions(): Map<String, Any> =
    extensions.extraProperties.properties
        .filterKeys { it.startsWith(LVM_SETTINGS_PREFIX) }
        .toSortedMap()
        .mapKeys { (key, _) -> key.removePrefix(LVM_SETTINGS_PREFIX) }
        .mapValues { (_, value) -> value.toString().let { it.toBooleanStrictOrNull() ?: it } }

/**
 * Dependencies that the build declares, as [describe] names them, by configuration name. Only configurations that hold
 * declarations count, such as `implementation` or `commonMainImplementation`: those that resolve or publish them
 * repeat what they inherit, and plugins fill them with tools such as compiler plugins.
 */
private fun List<Configuration>.declared(describe: (Dependency) -> String?): Map<String, List<String>> =
    filter { !it.isCanBeResolved && !it.isCanBeConsumed && KOTLIN_INTERNAL_BUCKETS.none(it.name::startsWith) }
        .associate { configuration -> configuration.name to configuration.dependencies.mapNotNull(describe).distinct().sorted() }
        .filterValues { it.isNotEmpty() }
        .toSortedMap()

/** Kotlin's buckets for Swift export and SwiftPM, which list other modules of the build rather than this one's dependencies. */
private val KOTLIN_INTERNAL_BUCKETS = listOf("swiftExport", "swiftPM")

private fun toolVersions(actual: String, minimum: String, tested: String): Map<String, String> =
    linkedMapOf("actual" to actual, "minimum" to minimum, "tested" to tested)

private val REPORT_DIRECTORY = listOf("build", "lvm", "report")
private const val DEFAULT_SOURCE = "default"
private const val MODULE_SOURCE = "module"
