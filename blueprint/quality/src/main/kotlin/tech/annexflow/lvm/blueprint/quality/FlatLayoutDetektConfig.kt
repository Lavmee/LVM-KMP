package tech.annexflow.lvm.blueprint.quality

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Writes [text] to [destination]; used for the detekt config of the flat source layout. */
internal abstract class WriteDetektConfig : DefaultTask() {
    @get:Input
    abstract val text: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun write() {
        destination.get().asFile.writeText(text.get())
    }
}

/** detekt's default path patterns for test code, plus the flat layout's platform test directories. */
private val TEST_EXCLUDES = listOf(
    "**/test/**",
    "**/androidTest/**",
    "**/commonTest/**",
    "**/jvmTest/**",
    "**/androidUnitTest/**",
    "**/androidInstrumentedTest/**",
    "**/jsTest/**",
    "**/iosTest/**",
    "**/test@*/**",
)

/** The rules whose default config (detekt 1.23) skips test code by path, grouped by rule set. */
private val RULES_EXCLUDING_TESTS = mapOf(
    "comments" to listOf(
        "KDocReferencesNonPublicProperty",
        "UndocumentedPublicClass",
        "UndocumentedPublicFunction",
        "UndocumentedPublicProperty",
    ),
    "complexity" to listOf("StringLiteralDuplication", "TooManyFunctions"),
    "exceptions" to listOf(
        "InstanceOfCheckForException",
        "ThrowingExceptionsWithoutMessageOrCause",
        "TooGenericExceptionCaught",
    ),
    "naming" to listOf("FunctionNaming"),
    "performance" to listOf("ForEachOnRange", "SpreadOperator"),
    "potential-bugs" to listOf("LateinitUsage", "UnsafeCallOnNullableType"),
    "style" to listOf("MagicNumber"),
)

/**
 * detekt config for the flat layout: the package-path check is off, because the root package is left out of the
 * directories, and the rules that skip test code also skip `test@<name>` directories. The quality plugin puts it before
 * the build's own config files, so those can override it.
 */
internal fun flatLayoutDetektConfig(): String = buildString {
    RULES_EXCLUDING_TESTS.forEach { (ruleSet, rules) ->
        appendLine("$ruleSet:")
        if (ruleSet == "naming") {
            appendLine("  InvalidPackageDeclaration:")
            appendLine("    active: false")
        }
        rules.forEach { rule ->
            // MagicNumber's default excludes also cover build scripts.
            val excludes = if (rule == "MagicNumber") TEST_EXCLUDES + "**/*.kts" else TEST_EXCLUDES
            appendLine("  $rule:")
            appendLine("    excludes: ${excludes.joinToString(prefix = "[", postfix = "]") { "'$it'" }}")
        }
    }
}
