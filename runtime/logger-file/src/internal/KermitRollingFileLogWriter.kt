/*
 * Copyright (c) 2024 Touchlab
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * Modified in LVM: copied from `co.touchlab.kermit.io.RollingFileLogWriter` of kermit-io 2.2.0. The log directory is
 * resolved on the writer's thread, and created there each time the log file is opened. The channel is cancelled when
 * the writer stops. Failures are printed with `println`, which works on every target. A logging call neither throws on
 * an interrupted thread nor clears its interrupt, and waits for room in the channel like any other call. The sink is
 * opened through a parameter, so tests can make writes fail.
 */

package tech.annexflow.lvm.logger.file.internal

import co.touchlab.kermit.DefaultFormatter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Message
import co.touchlab.kermit.MessageStringFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import kotlin.math.max
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

/**
 * Implements a log writer that writes log messages to a rolling file.
 *
 * It also deletes old log files when the maximum number of log files is reached. We simply keep
 * approximately [KermitRollingFileLogWriterConfig.rollOnSize] bytes in each log file,
 * and delete the oldest file when we have more than [KermitRollingFileLogWriterConfig.maxLogFiles].
 *
 * Formatting is governed by the passed [MessageStringFormatter], but we do prepend a timestamp by default.
 * Turn this off via [KermitRollingFileLogWriterConfig.prependTimestamp]
 *
 * Writes to the file are done by a different coroutine. The main reason for this is to make writes to the
 * log file sink thread-safe, and so that file rolling can be performed without additional synchronization
 * or locking. Log messages reach that coroutine through a channel that buffers up to [LOGGING_CHANNEL_CAPACITY]
 * of them, so logging threads normally hand a message off and return without waiting for the I/O. Once that
 * buffer is full, logging threads block until the writer catches up, which keeps memory bounded and limits how
 * many messages can be lost if the process dies.
 *
 * When writing fails with an `IOException`, for example on iOS while the device is locked and the files are
 * protected with `NSFileProtectionComplete`, the error is printed once and messages are dropped until the file can
 * be opened and written again.
 *
 * This is a copy of the kermit-io writer rather than a dependency on it because of how it treats the log directory:
 * it is resolved first thing on the writer's thread, not where the logger is built. Apps usually build their loggers
 * on the main thread, where resolving a cache directory and creating directories would block. The directory is
 * created each time the log file is opened, so a directory that was deleted while the app runs, such as a cleared
 * cache directory, comes back at the next roll or error. When the writer stops, for example because the directory
 * can't be resolved, the channel is cancelled, so logging calls return instead of waiting forever, including a call
 * that is already waiting.
 */
internal class KermitRollingFileLogWriter(
    private val config: KermitRollingFileLogWriterConfig,
    private val clock: Clock = Clock.System,
    private val messageStringFormatter: MessageStringFormatter = DefaultFormatter,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val openSink: (Path) -> RawSink = { path -> fileSystem.sink(path, append = true) },
) : LogWriter() {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val coroutineScope = CoroutineScope(
        newSingleThreadContext("RollingFileLogWriter") +
            SupervisorJob() +
            CoroutineName("RollingFileLogWriter") +
            CoroutineExceptionHandler { _, throwable ->
                // can't log it, we're the logger -- print to the console
                println("RollingFileLogWriter: Uncaught exception in writer coroutine")
                throwable.printStackTrace()
            },
    )

    private val loggingChannel: Channel<Buffer> = Channel(capacity = LOGGING_CHANNEL_CAPACITY)

    init {
        coroutineScope.launch {
            try {
                writer()
            } finally {
                // Without a writer, sends would wait forever. Cancel rather than close: close fails only the sends
                // that start after it, and a send that is already waiting would stay blocked.
                loggingChannel.cancel()
            }
        }
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        bufferLog(
            formatMessage(
                severity = severity,
                tag = Tag(tag),
                message = Message(message),
            ),
            throwable,
        )
    }

    private fun bufferLog(message: String, throwable: Throwable?) {
        val log = buildString {
            if (config.prependTimestamp) {
                append(clock.now().format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET))
                append(" ")
            }
            appendLine(message)
            if (throwable != null) {
                appendLine(throwable.stackTraceToString())
            }
        }
        loggingChannel.sendUninterruptibly(Buffer().apply { writeString(log) })
    }

    private fun formatMessage(severity: Severity, tag: Tag?, message: Message): String =
        messageStringFormatter.formatMessage(severity, if (config.logTag) tag else null, message)

    private fun shouldRollLogs(currentSize: Long, logFilePath: Path): Boolean =
        max(currentSize, fileSizeOrZero(logFilePath)) > config.rollOnSize

    private fun rollLogs() {
        if (fileSystem.exists(pathForLogIndex(config.maxLogFiles - 1))) {
            fileSystem.delete(pathForLogIndex(config.maxLogFiles - 1))
        }
        for (index in config.maxLogFiles - 2 downTo 0) {
            val sourcePath = pathForLogIndex(index)
            val targetPath = pathForLogIndex(index + 1)
            if (fileSystem.exists(sourcePath)) {
                try {
                    fileSystem.atomicMove(sourcePath, targetPath)
                } catch (e: IOException) {
                    // we can't log it, we're the logger -- print to the console
                    println(
                        "RollingFileLogWriter: Failed to roll log file $sourcePath to $targetPath " +
                            "(sourcePath exists=${fileSystem.exists(sourcePath)})\n${e.stackTraceToString()}",
                    )
                }
            }
        }
    }

    private fun pathForLogIndex(index: Int): Path = Path(
        config.logFilePath.value,
        if (index == 0) "${config.logFileName}.log" else "${config.logFileName}-$index.log",
    )

    private suspend fun writer() {
        // Resolved before the loop catches I/O errors: a directory lambda that throws ends the writer instead of
        // running again for every line.
        val directory = config.logFilePath.value
        val logFilePath = pathForLogIndex(0)

        fun createNewLogSink(): Sink {
            fileSystem.createDirectories(directory)
            return openSink(logFilePath).buffered()
        }

        var currentLogSink: Sink? = null
        // Track file size internally to avoid relying on filesystem metadata, which can be
        // stale on Windows while a write handle is open.
        var currentFileSize = fileSizeOrZero(logFilePath)

        // Tracks whether we are currently in an error state to avoid spamming on every write
        var ioErrorActive = false

        while (currentCoroutineContext().isActive) {
            // wait for data to be available, flush periodically
            val result = loggingChannel.receiveCatching()

            try {
                // check if logs need rolling
                if (shouldRollLogs(currentFileSize, logFilePath)) {
                    currentLogSink?.close()
                    rollLogs()
                    currentLogSink = createNewLogSink()
                    currentFileSize = 0
                }

                if (currentLogSink == null) {
                    currentLogSink = createNewLogSink()
                }

                val data = result.getOrNull()
                val bytesWritten = data?.size ?: 0
                data?.transferTo(currentLogSink)
                currentFileSize += bytesWritten

                // we could improve performance by flushing less frequently at the cost of potential data loss,
                // but this is a safe default
                currentLogSink.flush()

                if (ioErrorActive) {
                    println("RollingFileLogWriter: Log file access restored")
                    ioErrorActive = false
                }
            } catch (e: IOException) {
                // Keep the writer alive, so that logging resumes once the file can be written again.
                if (!ioErrorActive) {
                    println(
                        "RollingFileLogWriter: IOException writing to log file, some logs may be lost\n" +
                            e.stackTraceToString(),
                    )
                    ioErrorActive = true
                }
                currentLogSink?.closeQuietly()
                currentLogSink = null
                currentFileSize = fileSizeOrZero(logFilePath)
            }
        }

        currentLogSink?.close()
    }

    private fun fileSizeOrZero(path: Path) = fileSystem.metadataOrNull(path)?.size ?: 0
}

/** How many log messages [KermitRollingFileLogWriter] buffers before logging threads wait for the writer. */
private const val LOGGING_CHANNEL_CAPACITY = 64

private fun Sink.closeQuietly() {
    try {
        close()
    } catch (ignored: IOException) {
        // The sink failed already; the writer opens a new one.
    }
}
