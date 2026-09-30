package tech.annexflow.lvm.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.annexflow.lvm.logger.RecordingLogger.Entry

class LoggerTest {

    @Test
    fun `logger shortcuts log at their severity with an optional throwable`() {
        val logger = RecordingLogger()
        val failure = IllegalStateException()

        logger.d(tag = "T", message = 1)
        logger.i(tag = "T", message = null, throwable = failure)
        logger.w(tag = "T", message = "w")
        logger.e(tag = "T", message = "e", throwable = failure)

        assertEquals(
            listOf(
                Entry(Severity.DEBUG, "T", "1", null),
                Entry(Severity.INFO, "T", "null", failure),
                Entry(Severity.WARN, "T", "w", null),
                Entry(Severity.ERROR, "T", "e", failure),
            ),
            logger.entries,
        )
    }
}
