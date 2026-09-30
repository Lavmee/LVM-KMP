package tech.annexflow.lvm.logger.file

import co.touchlab.kermit.loggerConfigInit
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.files.Path
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriter
import tech.annexflow.lvm.logger.file.internal.KermitRollingFileLogWriterConfig
import tech.annexflow.lvm.logger.file.internal.asExternal
import co.touchlab.kermit.Logger as KermitLogger

/**
 * The `logs` directory in the app's cache directory, where [fileLogger] writes by default.
 *
 * The cache directory comes from FileKit, which finds it on its own on Android and iOS. On JVM, FileKit needs
 * `FileKit.init(appId)` from the app first (from `io.github.vinceglb:filekit-core`); apps that don't use FileKit pass
 * a directory to [fileLogger] instead.
 */
public val logsDirectoryPath: Path get() = FileKit.cacheDir.resolve(LOGS_DIRECTORY).toKotlinxIoPath()

/**
 * A [Logger] that writes to `<fileName>.log` in [directory]. When the file grows past 10 MB, it is renamed to
 * `<fileName>-1.log`, older files move up one index, and at most five files are kept.
 *
 * Building the logger does not touch the disk: [directory] is called, and the directory created, on the writer's own
 * thread. [Logger.log] returns once the writer has taken the line. If writing fails, for example because [directory]
 * throws, the error is printed and the logger drops the lines it hasn't written.
 */
public fun fileLogger(
    fileName: String,
    directory: () -> Path = { logsDirectoryPath },
): Logger {
    val writer = KermitRollingFileLogWriter(
        config = KermitRollingFileLogWriterConfig(
            logFileName = fileName,
            logFilePath = lazy(directory),
        ),
    )

    return KermitLogger(loggerConfigInit(writer)).asExternal()
}

internal const val LOGS_DIRECTORY: String = "logs"
