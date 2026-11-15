package tech.annexflow.lvm.logger.file

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import tech.annexflow.lvm.logger.Logger
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
        awaitText(Path(directory, "app.log"), "line")

        assertNotEquals(Thread.currentThread(), resolvedOn.get())
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
        // The writer takes no line before the directory resolves, so the buffer fills and the next call waits.
        logger.fillTheBuffer()

        val logging = thread(isDaemon = true) { logger.i(tag = "Test", message = "line") }
        check(logging.awaitBlocked()) { "the log call returned before the writer failed" }
        failDirectory.countDown()

        logging.join(TIMEOUT_MILLIS)
        assertFalse(logging.isAlive, "the log call still waits for a writer that failed")
    }

    /** Threads are interrupted, for example when an executor shuts down, and still log on their way out. */
    @Test
    fun `an interrupted thread waits for room, logs its line and keeps its interrupt`() {
        val resolveDirectory = CountDownLatch(1)
        val directory = Path(SystemTemporaryDirectory, "lvm-logger-interrupt-test-${Random.nextLong().toULong()}")
        val logger = fileLogger(fileName = "app") {
            resolveDirectory.await()
            directory
        }
        logger.fillTheBuffer()
        val failure = AtomicReference<Throwable>()
        val interruptKept = AtomicBoolean()

        val logging = thread(isDaemon = true) {
            Thread.currentThread().interrupt()
            runCatching { logger.i(tag = "Test", message = "interrupted line") }.onFailure(failure::set)
            interruptKept.set(Thread.currentThread().isInterrupted)
        }
        check(logging.awaitBlocked()) { "the interrupted log call didn't wait for room in the buffer" }
        resolveDirectory.countDown()
        logging.join(TIMEOUT_MILLIS)

        assertNull(failure.get())
        assertTrue(interruptKept.get())
        awaitText(Path(directory, "app.log"), "interrupted line")
        deleteRecursively(directory)
    }
}

private const val TIMEOUT_MILLIS = 10_000L

/** Logs as many lines as the writer buffers, so that the next call waits while the writer takes no line. */
private fun Logger.fillTheBuffer() = repeat(64) { i(tag = "Test", message = "filler $it") }

/** Waits until this thread blocks, and returns false if it ends first. */
private fun Thread.awaitBlocked(): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)
    while (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
        if (!isAlive) return false
        check(System.nanoTime() < deadline) { "the thread neither blocked nor ended" }
        Thread.onSpinWait()
    }
    return true
}
