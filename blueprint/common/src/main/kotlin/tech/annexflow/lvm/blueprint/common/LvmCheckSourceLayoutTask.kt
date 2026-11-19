package tech.annexflow.lvm.blueprint.common

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * Checks that the Kotlin and Java files of the flat layout sit in the directory of their package, and fails with every
 * file that doesn't. It reads only each file's package declaration, without the compiler; see [SourceLayoutCheck] for
 * the rules. On success it writes the module's root package and packages to [result].
 */
@InternalBlueprintApi
@CacheableTask
abstract class LvmCheckSourceLayoutTask : DefaultTask() {

    /** The Kotlin and Java files of each code root, as one file tree per root. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The module's directory, against which the error names files. */
    @get:Internal
    abstract val moduleDirectory: DirectoryProperty

    @get:Input
    abstract val modulePath: Property<String>

    @get:OutputFile
    abstract val result: RegularFileProperty

    @TaskAction
    fun check() {
        val module = moduleDirectory.get().asFile
        val files = mutableListOf<SourceFile>()
        sources.asFileTree.visit {
            if (isDirectory) return@visit
            // The path below the code root, so what remains of the path in the module is the code root itself.
            val inModule = file.relativeTo(module).invariantSeparatorsPath
            val root = inModule.removeSuffix("/" + relativePath.pathString)
            files += SourceFile(
                root = root,
                directories = relativePath.segments.dropLast(1),
                name = name,
                declaredPackage = PackageReader(file.readText(), nestedComments = name.endsWith(".kt")).read(),
            )
        }
        val check = SourceLayoutCheck(files)
        check.failure(modulePath.get())?.let { throw GradleException(it) }
        result.get().asFile.writeText(
            "root package: ${check.rootPackage.joinToString(".")}\n" + check.packages.joinToString("") { "$it\n" },
        )
    }
}

/** Name of the task that checks the packages of the flat layout. */
@InternalBlueprintApi
const val LVM_CHECK_SOURCE_LAYOUT_TASK: String = "lvmCheckSourceLayout"

/**
 * Registers [LvmCheckSourceLayoutTask] over the Kotlin and Java files of [codeRoots], the flat layout's directories of
 * the module's source sets relative to the module, such as `src` and `test@jvm`. The task has no sources, so Gradle
 * skips it, while [section]'s `flatLayout` or `checkSourceLayout` is off. Only these directories are read, so sources
 * that plugins such as KSP generate into the build directory are never checked. `module-info.java` declares a module
 * rather than a package and is left out.
 */
@InternalBlueprintApi
fun Project.registerSourceLayoutCheck(section: KotlinSection, codeRoots: Provider<List<String>>): TaskProvider<LvmCheckSourceLayoutTask> {
    val moduleDirectory = layout.projectDirectory
    val enabled = section.flatLayout.zip(section.checkSourceLayout) { flat, check -> flat && check }
    val trees = enabled.zip(codeRoots) { on, roots ->
        if (!on) return@zip emptyList()
        roots.distinct().sorted().map { root ->
            moduleDirectory.dir(root).asFileTree.matching {
                include("**/*.kt", "**/*.java")
                exclude("**/module-info.java")
            }
        }
    }
    val modulePath = path
    return tasks.register(LVM_CHECK_SOURCE_LAYOUT_TASK, LvmCheckSourceLayoutTask::class.java) {
        group = "verification"
        description = "Checks that the Kotlin and Java files of the flat source layout sit in the directory of their package."
        sources.from(trees)
        this.moduleDirectory.set(moduleDirectory)
        this.modulePath.set(modulePath)
        result.set(layout.buildDirectory.file("lvm/source-layout.txt"))
    }
}
