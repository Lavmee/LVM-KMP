package tech.annexflow.lvm.blueprint.common

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder

class LvmCompatibilityTest {

    @Test
    fun `compares dotted versions numerically and ignores qualifiers`() {
        assertTrue(LvmCompatibility.compare("2.4.20", "2.4.3") > 0)
        assertEquals(0, LvmCompatibility.compare("9.4", "9.4.0"))
        assertEquals(0, LvmCompatibility.compare("2.5.0-Beta1", "2.5.0"))
        assertTrue(LvmCompatibility.compare("8.13.0", "9.0.0") < 0)
    }

    @Test
    fun `classifies support`() {
        assertEquals(Support.TOO_OLD, LvmCompatibility.support("2.3.20", minimum = "2.4.0", tested = "2.4.20"))
        assertEquals(Support.SUPPORTED, LvmCompatibility.support("2.4.20", minimum = "2.4.0", tested = "2.4.20"))
        assertEquals(Support.NEWER_THAN_TESTED, LvmCompatibility.support("2.5.0", minimum = "2.4.0", tested = "2.4.20"))
    }

    @Test
    fun `classifies gradle versions against the gradle minimum`() {
        val minimum = LvmCompatibility.MIN_GRADLE
        assertEquals(Support.TOO_OLD, LvmCompatibility.support("9.5.1", minimum, tested = "9.7.1"))
        assertEquals(Support.SUPPORTED, LvmCompatibility.support("9.6", minimum, tested = "9.7.1"))
        assertEquals(Support.SUPPORTED, LvmCompatibility.support("9.6.0", minimum, tested = "9.7.1"))
        assertEquals(Support.NEWER_THAN_TESTED, LvmCompatibility.support("9.8-rc-1", minimum, tested = "9.7.1"))
    }

    @Test
    fun `requires the agp version lvm is tested with`() {
        assertEquals(Support.TOO_OLD, LvmCompatibility.support("9.3.1", LvmCompatibility.MIN_AGP, tested = "9.4.1"))
        assertEquals(Support.SUPPORTED, LvmCompatibility.support("9.4.0", LvmCompatibility.MIN_AGP, tested = "9.4.1"))
    }

    @Test
    fun `fails below the minimum and warns above the tested version`() {
        val logger = RecordingLogger()

        val error = assertFailsWith<GradleException> {
            requireSupported(logger.logger, "Tool", "1.9", minimum = "2.0", tested = "2.5")
        }
        assertEquals("LVM requires Tool 2.0 or newer; this build uses 1.9.", error.message)

        requireSupported(logger.logger, "Tool", "2.5", minimum = "2.0", tested = "2.5")
        assertEquals(emptyList(), logger.warnings)

        requireSupported(logger.logger, "Tool", "3.0", minimum = "2.0", tested = "2.5")
        assertEquals(listOf("LVM ${LvmBuildInfo.VERSION} was tested with Tool 2.5; this build uses 3.0."), logger.warnings)
    }

    @Test
    fun `the project check behaves like the logger check`() {
        val project = ProjectBuilder.builder().build()
        for (actual in listOf("1.9", "2.0", "2.5", "3.0")) {
            val withLogger = runCatching { requireSupported(RecordingLogger().logger, "Tool", actual, "2.0", "2.5") }
            val withProject = runCatching { project.requireSupported("Tool", actual, "2.0", "2.5") }
            assertEquals(withLogger.failure(), withProject.failure(), "Tool $actual")
        }
    }

    @Test
    fun `gradle older than the minimum fails with gradle, the minimum and the actual version`() {
        val expected = "LVM requires Gradle 9.6 or newer; this build uses 9.5.1."

        val full = assertFailsWith<GradleException> { requireSupportedGradle(RecordingLogger().logger, actual = "9.5.1") }
        assertEquals(expected, full.message)

        val minimumOnly = assertFailsWith<GradleException> { requireMinimumGradle(actual = "9.5.1") }
        assertEquals(expected, minimumOnly.message)
    }

    @Test
    fun `only the full gradle check warns about gradle newer than tested`() {
        val logger = RecordingLogger()

        requireSupportedGradle(logger.logger, actual = "99.0")
        assertEquals(
            listOf("LVM ${LvmBuildInfo.VERSION} was tested with Gradle ${LvmBuildInfo.TESTED_GRADLE}; this build uses 99.0."),
            logger.warnings,
        )

        // Takes no logger: project plugins leave the warning to the settings plugin.
        requireMinimumGradle(actual = "99.0")
    }

    @Test
    fun `the gradle running this build passes both gradle checks without a warning`() {
        val logger = RecordingLogger()
        requireSupportedGradle(logger.logger)
        requireMinimumGradle()
        assertEquals(emptyList(), logger.warnings)
    }

    private fun Result<Unit>.failure(): Pair<Class<*>, String?>? = exceptionOrNull()?.let { it.javaClass to it.message }

    /** A Gradle [Logger] that records `warn(String)` calls and ignores everything else. */
    private class RecordingLogger {
        val warnings = mutableListOf<String>()

        val logger: Logger = Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
            if (method.name == "warn" && args?.size == 1) warnings += args[0].toString()
            if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as Logger
    }
}
