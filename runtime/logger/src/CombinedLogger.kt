package tech.annexflow.lvm.logger

/**
 * A [Logger] that sends every message to this logger and then to [other].
 *
 * Combining combined loggers keeps one flat list, so each logger is called once, in order. A logger that throws stops
 * the message from reaching the loggers after it.
 */
public operator fun Logger.plus(other: Logger): Logger = CombinedLogger(loggers = flatten(this) + flatten(other))

private fun flatten(logger: Logger): List<Logger> = if (logger is CombinedLogger) logger.loggers else listOf(logger)

private class CombinedLogger(
    val loggers: List<Logger>,
) : Logger {
    override fun log(
        severity: Severity,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        loggers.forEach { logger ->
            logger.log(
                severity = severity,
                tag = tag,
                message = message,
                throwable = throwable,
            )
        }
    }
}
