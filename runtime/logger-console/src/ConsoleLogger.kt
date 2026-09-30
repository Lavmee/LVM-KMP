package tech.annexflow.lvm.logger.console

import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.console.internal.asExternal
import co.touchlab.kermit.Logger as KermitLogger

private val sharedConsoleLogger: Logger by lazy {
    KermitLogger(loggerConfigInit(platformLogWriter())).asExternal()
}

/**
 * A [Logger] that writes to the platform log: Logcat on Android, the system log on Apple platforms and the console
 * on JVM. On Apple platforms, stack traces go to standard output instead.
 */
public fun consoleLogger(): Logger = sharedConsoleLogger
