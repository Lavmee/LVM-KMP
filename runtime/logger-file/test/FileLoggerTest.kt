package tech.annexflow.lvm.logger.file

import co.touchlab.kermit.loggerConfigInit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriter
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriterConfig
import tech.annexflow.lvm.logger.file.internal.asExternal
import tech.annexflow.lvm.logger.i
import co.touchlab.kermit.Logger as KermitLogger

/** The writer runs on its own thread, so the tests wait for a line to reach the file before they read it. */
class FileLoggerTest {

    private val root = Path(SystemTemporaryDirectory, "lvm-logger-test-${Random.nextLong().toULong()}")

    @AfterTest
    fun deleteTheRoot() {
        // Best effort: a writer thread may still hold the last file open.
        runCatching { deleteRecursively(root) }
    }

    @Test
    fun `a line lands in the named log of a directory the writer creates`() {
        val directory = Path(root, "cache", "logs")
        assertFalse(SystemFileSystem.exists(directory))

        val logger = fileLogger(fileName = "app") { directory }
        logger.i(tag = "Import", message = "first line")

        assertContains(awaitText(Path(directory, "app.log"), "first line"), "Import")
    }

    @Test
    fun `a caller doesn't wait for a slow writer while the buffer has room`() {
        val directory = Path(root, "logs")
        val writerMayStart = CompletableDeferred<Unit>()
        val logger = fileLogger(fileName = "app") {
            runBlocking { writerMayStart.await() }
            directory
        }

        try {
            // The writer takes no line before the directory resolves, so every line waits in the buffer of 64.
            val returned = runOnAnotherThread { repeat(64) { logger.i(tag = "Test", message = "line $it") } }
            assertTrue(returned, "a log call waited for the writer while the buffer had room")
        } finally {
            writerMayStart.complete(Unit)
        }
        awaitText(Path(directory, "app.log"), "line 63")
    }

    @Test
    fun `logging resumes after a write fails`() {
        val sinks = FailingSinks()
        val logger = KermitLogger(
            loggerConfigInit(
                KermitRollingFileLogWriter(
                    config = KermitRollingFileLogWriterConfig(logFileName = "app", logFilePath = lazy { root }),
                    openSink = sinks::open,
                ),
            ),
        ).asExternal()
        val log = Path(root, "app.log")

        logger.i(tag = "Test", message = "before the failure")
        awaitText(log, "before the failure")
        sinks.failNextWrite()
        logger.i(tag = "Test", message = "line that fails")
        logger.i(tag = "Test", message = "after the failure")

        awaitText(log, "after the failure")
        assertTrue(sinks.failed, "no write failed")
    }

    @Test
    fun `a deleted directory is created again when the log rolls`() {
        val directory = Path(root, "logs")
        val logger = smallRollingLogger(directory)
        val log = Path(directory, "app.log")
        // Longer than the log may grow, so the next line rolls it.
        val longText = "x".repeat(SMALL_ROLL_SIZE.toInt())

        logger.i(tag = "Test", message = "first $longText")
        awaitText(log, "first")
        deleteRecursively(directory)
        logger.i(tag = "Test", message = "second $longText")

        awaitText(log, "second")
    }

    @Test
    fun `logs roll past 10 MB and five files are kept`() {
        val directory = Path(root, "logs")
        val logger = fileLogger(fileName = "app") { directory }
        val text = "x".repeat(10_000)

        // A little over five full files: the fifth roll would make a sixth file.
        repeat(5_500) { logger.i(tag = "Test", message = text) }
        logger.i(tag = "Test", message = "last line")
        awaitText(Path(directory, "app.log"), "last line")

        val maxSize = 10L * 1024 * 1024
        for (index in 1..4) {
            val size = assertNotNull(SystemFileSystem.metadataOrNull(Path(directory, "app-$index.log"))).size
            assertTrue(size in (maxSize + 1)..(maxSize + text.length + LINE_OVERHEAD), "app-$index.log has $size bytes")
        }
        assertFalse(SystemFileSystem.exists(Path(directory, "app-5.log")))
    }

    @Test
    fun `logging keeps returning when the directory can't be resolved`() {
        val logger = fileLogger(fileName = "app") { error("no directory") }

        // More lines than the buffer holds: without a live writer, a send would wait forever, so the test fails by
        // hanging.
        repeat(100) { logger.i(tag = "Test", message = "line") }
    }

    private fun smallRollingLogger(directory: Path): Logger = KermitLogger(
        loggerConfigInit(
            KermitRollingFileLogWriter(
                config = KermitRollingFileLogWriterConfig(
                    logFileName = "app",
                    logFilePath = lazy { directory },
                    rollOnSize = SMALL_ROLL_SIZE,
                    prependTimestamp = false,
                ),
            ),
        ),
    ).asExternal()
}

private const val SMALL_ROLL_SIZE = 100L

/** The timestamp, severity and tag in front of a message. */
private const val LINE_OVERHEAD = 200

/** Runs [block] on another thread and returns whether it finished in time. A blocked [block] stays blocked. */
private fun runOnAnotherThread(block: () -> Unit): Boolean {
    val job = CoroutineScope(Dispatchers.Default).launch { block() }
    return runBlocking { withTimeoutOrNull(10.seconds) { job.join() } } != null
}
