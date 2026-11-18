package tech.annexflow.lvm.blueprint.multiplatform

import java.io.File
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/** How many Java files an error names before it only counts the rest. */
private const val LISTED_JAVA_FILES = 5

/**
 * The flat layout's directory for a source set: `src` for `commonMain`, `test` for `commonTest`, `src@<name>` for
 * `<name>Main` and `test@<name>` for `<name>Test`. `null` for a source set whose name ends in neither.
 */
internal fun flatSourceDirectory(sourceSetName: String): String? = when {
    sourceSetName == "commonMain" -> "src"
    sourceSetName == "commonTest" -> "test"
    sourceSetName.endsWith("Main") -> "src@" + sourceSetName.removeSuffix("Main")
    sourceSetName.endsWith("Test") -> "test@" + sourceSetName.removeSuffix("Test")
    else -> null
}

/**
 * Adds the flat layout's directory to every source set while [flatLayout] is on.
 *
 * The directory is added through a provider, so `flatLayout` can be set anywhere in the build script. It is added next
 * to the conventional one rather than instead of it, so source directories that other plugins register stay. A module
 * that still has `src/<sourceSet>/kotlin` fails, because `src` would compile those files a second time.
 *
 * Kotlin compiles against the Java files in these directories, but only javac compiles them into the build. Each JVM
 * compilation has a Java source set with the name of its default source set, which gets the same directory. An Android
 * compilation compiles Java only after `withJava()`, which [configureAndroidTarget] checks. Java files of every other
 * source set, such as those in `src/`, fail the build, because they would be missing at runtime.
 */
internal fun Project.configureSourceLayout(multiplatform: KotlinMultiplatformExtension, flatLayout: Provider<Boolean>) {
    val moduleDirectory = layout.projectDirectory
    val modulePath = path
    // Filled while the build script creates targets, and read by the providers below once it is done.
    val sourceSetsWithJava = mutableSetOf<String>()
    multiplatform.targets.matching { it.platformType == KotlinPlatformType.jvm }.configureEach {
        compilations.configureEach {
            val sourceSetName = defaultSourceSet.name
            val directory = flatSourceDirectory(sourceSetName) ?: return@configureEach
            // KGP sets the directories of the Java source set after creating it, so a directory added when the source
            // set is created would be dropped. By the time KGP adds the compilation, they are set.
            val javaSourceSet = this@configureSourceLayout.extensions.findByType(JavaPluginExtension::class.java)
                ?.sourceSets?.findByName(sourceSetName) ?: return@configureEach
            sourceSetsWithJava += sourceSetName
            javaSourceSet.java.srcDir(
                flatLayout.map { flat -> if (flat) listOf(moduleDirectory.dir(directory)) else emptyList() },
            )
        }
    }
    multiplatform.targets.matching { it.platformType == KotlinPlatformType.androidJvm }.configureEach {
        compilations.configureEach { sourceSetsWithJava += defaultSourceSet.name }
    }
    multiplatform.sourceSets.configureEach {
        val sourceSetName = name
        val directory = flatSourceDirectory(sourceSetName) ?: return@configureEach
        kotlin.srcDir(
            flatLayout.map { flat ->
                if (!flat) return@map emptyList()
                if (moduleDirectory.dir("src/$sourceSetName/kotlin").asFile.isDirectory) {
                    throw InvalidUserDataException(
                        "$modulePath uses the flat source layout (lvm.kotlin.flatLayout) but still has " +
                            "src/$sourceSetName/kotlin. Move its files to $directory/, or turn the flat layout off.",
                    )
                }
                if (sourceSetName !in sourceSetsWithJava) {
                    requireNoJavaFiles(
                        modulePath,
                        moduleDirectory,
                        directory,
                        "Move them to the directory of a JVM target, such as src@jvm/, or of the Android target " +
                            "after calling withJava() in kotlin { android { } }.",
                    )
                }
                listOf(moduleDirectory.dir(directory))
            },
        )
    }
}

/** Fails while the flat [directory] of the module holds Java files, naming them and ending with [hint]. */
internal fun requireNoJavaFiles(modulePath: String, moduleDirectory: Directory, directory: String, hint: String) {
    val root = moduleDirectory.asFile
    val javaFiles = root.resolve(directory).javaFiles()
    if (javaFiles.isEmpty()) return
    val listed = javaFiles.take(LISTED_JAVA_FILES).joinToString { it.relativeTo(root).invariantSeparatorsPath }
    val more = if (javaFiles.size > LISTED_JAVA_FILES) " and ${javaFiles.size - LISTED_JAVA_FILES} more" else ""
    throw InvalidUserDataException(
        "$modulePath has Java files in $directory/, where javac doesn't compile them: $listed$more. $hint",
    )
}

/**
 * The Java files in this directory and below it. It lists directories itself rather than through `File.walk()`, so the
 * configuration cache sees the listings and checks again once a file is added.
 */
private fun File.javaFiles(): List<File> =
    listFiles().orEmpty().sortedBy(File::getName).flatMap { file ->
        when {
            file.isDirectory -> file.javaFiles()
            file.name.endsWith(".java") -> listOf(file)
            else -> emptyList()
        }
    }
