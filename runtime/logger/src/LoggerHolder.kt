package tech.annexflow.lvm.logger

/**
 * A class that logs through [logger]. Its `log`, `d`, `i`, `w` and `e` extensions tag messages with the class's simple
 * name, which obfuscation can shorten in release builds.
 */
public interface LoggerHolder {
    public val logger: Logger
}

/** Logs [message] at [severity], tagged with this class's simple name unless [tag] is given. */
public fun LoggerHolder.log(
    severity: Severity,
    message: Any?,
    throwable: Throwable? = null,
    tag: String = this::class.simpleName.orEmpty(),
) {
    logger.log(severity = severity, tag = tag, message = message.toString(), throwable = throwable)
}

/**
 * Logs [message] at [Severity.DEBUG]. Pass an exception as [throwable] to log its stack trace; as [message] it is
 * logged as text only.
 */
public fun LoggerHolder.d(message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.DEBUG, message = message, throwable = throwable)
}

/**
 * Logs [message] at [Severity.INFO]. Pass an exception as [throwable] to log its stack trace; as [message] it is logged
 * as text only.
 */
public fun LoggerHolder.i(message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.INFO, message = message, throwable = throwable)
}

/**
 * Logs [message] at [Severity.WARN]. Pass an exception as [throwable] to log its stack trace; as [message] it is logged
 * as text only.
 */
public fun LoggerHolder.w(message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.WARN, message = message, throwable = throwable)
}

/**
 * Logs [message] at [Severity.ERROR]. Pass an exception as [throwable] to log its stack trace; as [message] it is
 * logged as text only.
 */
public fun LoggerHolder.e(message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.ERROR, message = message, throwable = throwable)
}
