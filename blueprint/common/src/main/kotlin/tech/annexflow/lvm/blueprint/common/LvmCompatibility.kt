package tech.annexflow.lvm.blueprint.common

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.util.GradleVersion

enum class Support { TOO_OLD, SUPPORTED, NEWER_THAN_TESTED }

object LvmCompatibility {
    /** Compares dotted numeric versions; qualifiers such as `-RC1` are ignored. */
    fun compare(left: String, right: String): Int {
        val l = numbers(left)
        val r = numbers(right)
        for (i in 0 until maxOf(l.size, r.size)) {
            val result = l.getOrElse(i) { 0 }.compareTo(r.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    fun support(actual: String, minimum: String, tested: String): Support = when {
        compare(actual, minimum) < 0 -> Support.TOO_OLD
        compare(actual, tested) > 0 -> Support.NEWER_THAN_TESTED
        else -> Support.SUPPORTED
    }

    private fun numbers(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
}

/** Fails when [actual] is older than [minimum]; warns on [logger] when it is newer than [tested]. */
fun requireSupported(logger: Logger, tool: String, actual: String, minimum: String, tested: String) {
    when (LvmCompatibility.support(actual, minimum, tested)) {
        Support.TOO_OLD -> throw tooOld(tool, minimum, actual)
        Support.NEWER_THAN_TESTED -> logger.warn("LVM ${LvmBuildInfo.VERSION} was tested with $tool $tested; this build uses $actual.")
        Support.SUPPORTED -> Unit
    }
}

fun Project.requireSupported(tool: String, actual: String, minimum: String, tested: String) {
    requireSupported(logger, tool, actual, minimum, tested)
}

/**
 * The full Gradle check: fails below [LvmBuildInfo.MIN_GRADLE] and warns above the tested version.
 * Only the settings plugin runs it, so the warning appears once per build rather than once per module.
 */
fun requireSupportedGradle(logger: Logger, actual: String = GradleVersion.current().version) {
    requireSupported(logger, GRADLE, actual, LvmBuildInfo.MIN_GRADLE, LvmBuildInfo.TESTED_GRADLE)
}

/** Fails below [LvmBuildInfo.MIN_GRADLE] without warning, so builds without the settings plugin are protected too. */
fun requireMinimumGradle(actual: String = GradleVersion.current().version) {
    val minimum = LvmBuildInfo.MIN_GRADLE
    if (LvmCompatibility.compare(actual, minimum) < 0) throw tooOld(GRADLE, minimum, actual)
}

private const val GRADLE = "Gradle"

private fun tooOld(tool: String, minimum: String, actual: String): GradleException =
    GradleException("LVM requires $tool $minimum or newer; this build uses $actual.")
