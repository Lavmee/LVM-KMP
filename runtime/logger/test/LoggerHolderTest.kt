package tech.annexflow.lvm.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.annexflow.lvm.logger.RecordingLogger.Entry

class LoggerHolderTest {

    private class Checkout(override val logger: Logger) : LoggerHolder

    @Test
    fun `holder logs are tagged with the class name`() {
        val logger = RecordingLogger()

        Checkout(logger).i("paid")

        assertEquals(listOf(Entry(Severity.INFO, "Checkout", "paid", null)), logger.entries)
    }

    @Test
    fun `each holder function logs at its severity with the throwable`() {
        val logger = RecordingLogger()
        val failure = IllegalStateException()
        val holder = Checkout(logger)

        holder.d("d", failure)
        holder.i("i", failure)
        holder.w("w", failure)
        holder.e("e", failure)

        assertEquals(
            listOf(
                Entry(Severity.DEBUG, "Checkout", "d", failure),
                Entry(Severity.INFO, "Checkout", "i", failure),
                Entry(Severity.WARN, "Checkout", "w", failure),
                Entry(Severity.ERROR, "Checkout", "e", failure),
            ),
            logger.entries,
        )
    }

    @Test
    fun `log uses an explicit tag`() {
        val logger = RecordingLogger()

        Checkout(logger).log(Severity.INFO, "m", tag = "Custom")

        assertEquals(listOf(Entry(Severity.INFO, "Custom", "m", null)), logger.entries)
    }
}
