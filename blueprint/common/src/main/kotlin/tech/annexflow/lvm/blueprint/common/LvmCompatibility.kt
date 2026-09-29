package tech.annexflow.lvm.blueprint.common

import org.gradle.api.GradleException
import org.gradle.api.Project

enum class Support { TOO_OLD, SUPPORTED, NEWER_THAN_TESTED }

object LvmCompatibility {
    const val MIN_KOTLIN: String = "2.4.0"
    const val MIN_AGP: String = "9.0.0"

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

fun Project.requireSupported(tool: String, actual: String, minimum: String, tested: String) {
    when (LvmCompatibility.support(actual, minimum, tested)) {
        Support.TOO_OLD -> throw GradleException("LVM requires $tool $minimum or newer; this build uses $actual.")
        Support.NEWER_THAN_TESTED -> logger.warn("LVM ${LvmBuildInfo.VERSION} was tested with $tool $tested; this build uses $actual.")
        Support.SUPPORTED -> Unit
    }
}
