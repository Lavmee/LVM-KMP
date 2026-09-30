package tech.annexflow.lvm.logger.file

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        check(logging.awaitBlocked()) { "the log call returned before the writer failed" }
        failDirectory.countDown()

        logging.join(TIMEOUT_MILLIS)
        assertFalse(logging.isAlive, "the log call still waits for a writer that failed")
    }

    /** Threads are interrupted, for example when an executor shuts down, and still log on their way out. */
    @Test
    fun `an interrupted thread logs its line and keeps its interrupt`() {
        val resolveDirectory = CountDownLatch(1)
        val directory = Path(SystemTemporaryDirectory, "lvm-logger-interrupt-test-${Random.nextLong().toULong()}")
        val logger = fileLogger(fileName = "app") {
            resolveDirectory.await()
            directory
        }
        val failure = AtomicReference<Throwable>()
        val interruptKept = AtomicBoolean()

        val logging = thread(isDaemon = true) {
            Thread.currentThread().interrupt()
            runCatching { logger.i(tag = "Test", message = "first line") }.onFailure(failure::set)
            interruptKept.set(Thread.currentThread().isInterrupted)
        }
        // The writer takes no line before the directory resolves, so a working log call blocks in the channel.
        logging.awaitBlocked()
        resolveDirectory.countDown()
        logging.join(TIMEOUT_MILLIS)
        logger.i(tag = "Test", message = "second line")

        assertNull(failure.get())
        assertTrue(interruptKept.get())
        assertContains(readText(Path(directory, "app.log")), "first line")
        deleteRecursively(directory)
    }
}

private const val TIMEOUT_MILLIS = 10_000L

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
