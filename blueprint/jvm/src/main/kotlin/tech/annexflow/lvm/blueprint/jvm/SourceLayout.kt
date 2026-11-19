package tech.annexflow.lvm.blueprint.jvm

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import tech.annexflow.lvm.blueprint.common.KotlinSection
import tech.annexflow.lvm.blueprint.common.registerSourceLayoutCheck

/**
 * The flat layout's directory for a source set: `src` for `main`, `test` and `testFixtures` for those source sets, and
 * `src@<name>` for any other, such as `src@integrationTest`.
 */
internal fun flatSourceDirectory(sourceSetName: String): String = when (sourceSetName) {
    "main" -> "src"
    "test", "testFixtures" -> sourceSetName
    else -> "src@$sourceSetName"
}

/**
 * Adds the flat layout's directory to the Kotlin and the Java sources of every source set while `flatLayout` of
 * [section] is on, so Java files in it compile too. Resources keep their conventional directories, such as
 * `src/main/resources`, as in the other plugins.
 *
 * The directory is added through a provider, so `flatLayout` can be set anywhere in the build script. A module that
 * still has `src/<sourceSet>/kotlin` or `src/<sourceSet>/java` fails, because `src` would compile those files a second
 * time. Every Kotlin compilation waits for the check of the packages in these directories.
 */
internal fun Project.configureSourceLayout(kotlinExtension: KotlinJvmProjectExtension, section: KotlinSection) {
    val moduleDirectory = layout.projectDirectory
    val modulePath = path
    val flatLayout = section.flatLayout
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val check = registerSourceLayoutCheck(section, provider { sourceSets.names.map(::flatSourceDirectory) })
    tasks.withType(KotlinCompilationTask::class.java).configureEach { dependsOn(check) }
    sourceSets.configureEach {
        val sourceSetName = name
        val directory = flatSourceDirectory(sourceSetName)
        val directories = flatLayout.map { flat ->
            if (!flat) return@map emptyList()
            for (conventional in listOf("kotlin", "java")) {
                if (moduleDirectory.dir("src/$sourceSetName/$conventional").asFile.isDirectory) {
                    throw InvalidUserDataException(
                        "$modulePath uses the flat source layout (lvm.kotlin.flatLayout) but still has " +
                            "src/$sourceSetName/$conventional. Move its files to $directory/, or turn the flat layout off.",
                    )
                }
            }
            listOf(moduleDirectory.dir(directory))
        }
        java.srcDir(directories)
        kotlinExtension.sourceSets.matching { it.name == sourceSetName }.configureEach { kotlin.srcDir(directories) }
    }
}
