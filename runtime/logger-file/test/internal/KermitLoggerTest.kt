package tech.annexflow.lvm.logger.file.internal

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.Test
import kotlin.test.assertEquals
import tech.annexflow.lvm.logger.Severity
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity as KermitSeverity

class KermitLoggerTest {

    @Test
    fun `every field of a line reaches the Kermit writer`() {
        val writer = RecordingLogWriter()
        val logger = KermitLogger(loggerConfigInit(writer)).asExternal()
        val throwable = IllegalStateException("failure")

        logger.log(severity = Severity.WARN, tag = "Tag", message = "Message", throwable = throwable)

        val expected = Line(severity = KermitSeverity.Warn, tag = "Tag", message = "Message", throwable = throwable)
        assertEquals(listOf(expected), writer.lines)
    }
}

private data class Line(val severity: KermitSeverity, val tag: String, val message: String, val throwable: Throwable?)

private class RecordingLogWriter : LogWriter() {
    val lines = mutableListOf<Line>()

    override fun log(severity: KermitSeverity, message: String, tag: String, throwable: Throwable?) {
        lines += Line(severity = severity, tag = tag, message = message, throwable = throwable)
    }
}
