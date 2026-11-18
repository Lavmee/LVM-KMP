package tech.annexflow.lvm.blueprint.android

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

/** Roots of the flat layout besides `src`, longest first, so `testFixturesDebug` isn't read as `test` + `FixturesDebug`. */
private val TEST_ROOTS = listOf("androidTest", "testFixtures", "test")

/**
 * The flat layout's directory for an Android source set: `src` for `main`, `src@<name>` for a build type, a flavor or a
 * variant, and `test`, `androidTest` or `testFixtures` followed by `@<variant>` for their source sets:
 * `testDebug` -> `test@debug`.
 */
internal fun flatSourceDirectory(sourceSetName: String): String {
    for (root in TEST_ROOTS) {
        if (sourceSetName == root) return root
        val variant = sourceSetName.removePrefix(root)
        if (variant != sourceSetName && variant.first().isUpperCase()) {
            return root + "@" + variant.replaceFirstChar(Char::lowercaseChar)
        }
    }
    return if (sourceSetName == "main") "src" else "src@$sourceSetName"
}

/**
 * Adds the flat layout's directory to the Kotlin and the Java sources of every source set. Kotlin reads Java files
 * from its own directories too, but only javac compiles them into the APK, so a Java file in the flat directory needs
 * both. Resources, `res` and the manifest keep their conventional directories. A module that still has Kotlin or Java
 * sources in `src/<sourceSet>/kotlin` or `src/<sourceSet>/java` fails, because `src` would compile those files a
 * second time.
 */
internal fun Project.configureSourceLayout(extension: CommonExtension, flatLayout: Boolean) {
    if (!flatLayout) return
    extension.sourceSets.configureEach {
        val directory = flatSourceDirectory(name)
        for (conventional in listOf("kotlin", "java")) {
            if (file("src/$name/$conventional").isDirectory) {
                throw InvalidUserDataException(
                    "$path uses the flat source layout (lvm.kotlin.flatLayout) but still has src/$name/$conventional. " +
                        "Move its files to $directory/, or turn the flat layout off.",
                )
            }
        }
        kotlin.directories.add(directory)
        java.directories.add(directory)
    }
}
