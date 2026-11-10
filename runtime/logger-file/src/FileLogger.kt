package tech.annexflow.lvm.logger.file

import co.touchlab.kermit.loggerConfigInit
import kotlinx.io.files.Path
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriter
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriterConfig
import tech.annexflow.lvm.logger.file.internal.asExternal
import co.touchlab.kermit.Logger as KermitLogger

/**
 * A [Logger] that writes to `<fileName>.log` in [directory]. When the file grows past 10 MB, it is renamed to
 * `<fileName>-1.log`, older files move up one index, and at most five files are kept.
 *
 * Building the logger does not touch the disk: [directory] is called, and the directory created, on the writer's own
 * thread. So [directory] may do disk work itself, such as reading the cache directory of `AppDirectories` from
 * `lvm-io`: `fileLogger("app") { Path(appDirectories.cache, "logs") }`.
 *
 * [Logger.log] returns once the writer has taken the line. On JVM and Android, a line is dropped when the logging
 * thread is interrupted while it waits for the writer. If writing fails, for example because [directory] throws, the
 * error is printed once and every later line is dropped.
 *
 * Each call starts a writer thread that runs until the process ends. Build one logger per file and share it: two
 * loggers on the same file lose lines when they roll it.
 */
public fun fileLogger(fileName: String, directory: () -> Path): Logger {
    val writer = KermitRollingFileLogWriter(
        config = KermitRollingFileLogWriterConfig(
            logFileName = fileName,
            logFilePath = lazy(directory),
        ),
    )

    return KermitLogger(loggerConfigInit(writer)).asExternal()
}
