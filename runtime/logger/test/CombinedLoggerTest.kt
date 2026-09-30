package tech.annexflow.lvm.logger

import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedLoggerTest {

    @Test
    fun `plus sends every message to both loggers`() {
        val first = RecordingLogger()
        val second = RecordingLogger()

        (first + second).log(Severity.WARN, "Tag", "message", null)

        val expected = listOf(RecordingLogger.Entry(Severity.WARN, "Tag", "message", null))
        assertEquals(expected, first.entries)
        assertEquals(expected, second.entries)
    }

    @Test
    fun `combined loggers are called in the order they were added`() {
        val calls = mutableListOf<String>()
        fun named(name: String) = Logger { _, _, _, _ -> calls += name }

        (named("a") + named("b") + named("c")).log(Severity.INFO, "Tag", "message", null)
        (named("d") + (named("e") + named("f"))).log(Severity.INFO, "Tag", "message", null)

        assertEquals(listOf("a", "b", "c", "d", "e", "f"), calls)
    }
}
