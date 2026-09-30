package tech.annexflow.lvm.logger.file

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import tech.annexflow.lvm.logger.i

class FileLoggerThreadTest {

    /** Apps build loggers on the main thread, and resolving the directory is disk work. */
    @Test
    fun `the directory is resolved off the thread that builds the logger`() {
        val resolvedOn = AtomicReference<Thread>()
        val directory = Path(SystemTemporaryDirectory, "lvm-logger-thread-test-${Random.nextLong().toULong()}")

        val logger = fileLogger(fileName = "app") {
            resolvedOn.set(Thread.currentThread())
            directory
        }
        logger.i(tag = "Test", message = "line")

        val thread = assertNotNull(resolvedOn.get(), "the writer took a line before resolving the directory")
        assertNotEquals(Thread.currentThread(), thread)
        deleteRecursively(directory)
    }

    /** A log call can already be waiting in the channel when the writer fails. */
    @Test
    fun `a log call waiting for the writer returns when the writer fails`() {
        val failDirectory = CountDownLatch(1)
        val logger = fileLogger(fileName = "app") {
            failDirectory.await()
            error("no directory")
        }

        val logging = thread(isDaemon = true) { logger.i(tag = "Test", message = "line") }
        // The writer takes no line before the directory resolves, so the log call parks in the channel.
        while (logging.state != Thread.State.WAITING && logging.state != Thread.State.TIMED_WAITING) {
            check(logging.isAlive) { "the log call returned before the writer failed" }
            Thread.onSpinWait()
        }
        failDirectory.countDown()

        logging.join(TIMEOUT_MILLIS)
        assertFalse(logging.isAlive, "the log call still waits for a writer that failed")
    }
}

private const val TIMEOUT_MILLIS = 10_000L
