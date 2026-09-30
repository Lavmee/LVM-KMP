package tech.annexflow.lvm.logger

/** Receives log messages and decides where they go. Combine several loggers with [plus]. */
public fun interface Logger {
    /** Logs [message] from [tag] at [severity], with an optional [throwable]. */
    public fun log(severity: Severity, tag: String, message: String, throwable: Throwable?)
}

/**
 * Logs [message] at [Severity.DEBUG]. Pass an exception as [throwable] to log its stack trace; as [message] it is
 * logged as text only.
 */
public fun Logger.d(tag: String, message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.DEBUG, tag = tag, message = message.toString(), throwable = throwable)
}

/**
 * Logs [message] at [Severity.INFO]. Pass an exception as [throwable] to log its stack trace; as [message] it is logged
 * as text only.
 */
public fun Logger.i(tag: String, message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.INFO, tag = tag, message = message.toString(), throwable = throwable)
}

/**
 * Logs [message] at [Severity.WARN]. Pass an exception as [throwable] to log its stack trace; as [message] it is logged
 * as text only.
 */
public fun Logger.w(tag: String, message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.WARN, tag = tag, message = message.toString(), throwable = throwable)
}

/**
 * Logs [message] at [Severity.ERROR]. Pass an exception as [throwable] to log its stack trace; as [message] it is
 * logged as text only.
 */
public fun Logger.e(tag: String, message: Any?, throwable: Throwable? = null) {
    log(severity = Severity.ERROR, tag = tag, message = message.toString(), throwable = throwable)
}
