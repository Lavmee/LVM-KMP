package tech.annexflow.lvm.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.annexflow.lvm.logger.RecordingLogger.Entry

class LoggerTest {

    @Test
    fun `logger shortcuts log at their severity`() {
        val logger = RecordingLogger()
        val failure = IllegalStateException()

        logger.d(tag = "T", message = 1)
        logger.i(tag = "T", message = null)
        logger.w(tag = "T", message = "w", throwable = failure)
        logger.e(tag = "T", message = "e")

        assertEquals(
            listOf(
                Entry(Severity.DEBUG, "T", "1", null),
                Entry(Severity.INFO, "T", "null", null),
                Entry(Severity.WARN, "T", "w", failure),
                Entry(Severity.ERROR, "T", "e", null),
            ),
            logger.entries,
        )
    }
}
