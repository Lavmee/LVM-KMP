import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Points fixture tests at a copy of blueprint's sources and at the TestKit directory they run in, and makes those
 * sources an input of the tests.
 */
abstract class BlueprintFixtureDir : CommandLineArgumentProvider {
    /** Root of the copy. Its location is not an input. */
    @get:Internal
    abstract val root: DirectoryProperty

    /** The Gradle user home of the fixture builds. It holds caches only, so it is not an input. */
    @get:Internal
    abstract val testKitDir: DirectoryProperty

    /** The copied sources. Nested fixture builds write their own outputs into the copy, so those are not inputs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sources: FileTree
        get() = root.asFileTree.matching { exclude(*BUILD_OUTPUTS) }

    override fun asArguments(): Iterable<String> =
        listOf(
            "-Dlvm.blueprint.dir=" + root.get().dir("blueprint").asFile.absolutePath,
            "-Dlvm.blueprint.testKitDir=" + testKitDir.get().asFile.absolutePath,
        )

    companion object {
        val BUILD_OUTPUTS = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
}
