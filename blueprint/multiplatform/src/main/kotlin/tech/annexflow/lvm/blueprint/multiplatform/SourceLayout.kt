package tech.annexflow.lvm.blueprint.multiplatform

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
 */
internal fun Project.configureSourceLayout(multiplatform: KotlinMultiplatformExtension, flatLayout: Provider<Boolean>) {
    val moduleDirectory = layout.projectDirectory
    val modulePath = path
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
                listOf(moduleDirectory.dir(directory))
            },
        )
    }
}
