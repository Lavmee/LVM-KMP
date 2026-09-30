package tech.annexflow.lvm.logger.file

import co.touchlab.kermit.loggerConfigInit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriter
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriterConfig
import tech.annexflow.lvm.logger.file.internal.asExternal
import tech.annexflow.lvm.logger.i
import co.touchlab.kermit.Logger as KermitLogger

/**
 * Lines reach the writer through a channel without a buffer: [Logger.log] returns when the writer has taken the line,
 * and the writer takes the next line only after writing and flushing the previous one. So once a second line has been
 * logged, the first one is in the file, and the tests need no waiting.
 */
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
        logger.i(tag = "Import", message = "second line")

        val written = readText(Path(directory, "app.log"))
        assertContains(written, "Import")
        assertContains(written, "first line")
    }

    @Test
    fun `a full log rolls over and files beyond the limit are dropped`() {
        val writer = KermitRollingFileLogWriter(
            config = KermitRollingFileLogWriterConfig(
                logFileName = "app",
                logFilePath = lazy { root },
                rollOnSize = 100,
                maxLogFiles = 2,
            ),
        )
        val logger = KermitLogger(loggerConfigInit(writer)).asExternal()

        repeat(20) { logger.i(tag = "Test", message = "a line long enough to fill a small log file quickly") }

        assertTrue(SystemFileSystem.exists(Path(root, "app.log")))
        assertTrue(SystemFileSystem.exists(Path(root, "app-1.log")))
        assertFalse(SystemFileSystem.exists(Path(root, "app-2.log")))
    }

    @Test
    fun `logging keeps returning when the directory can't be resolved`() {
        val logger = fileLogger(fileName = "app") { error("no directory") }

        // Without a live writer, a send on the channel would wait forever; the test fails by hanging.
        repeat(3) { logger.i(tag = "Test", message = "line") }
    }
}
