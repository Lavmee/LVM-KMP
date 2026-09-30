@file:Suppress("NOTHING_TO_INLINE")

package tech.annexflow.lvm.logger

/** Receives log messages and decides where they go. Combine several loggers with [plus]. */
public fun interface Logger {
    /** Logs [message] from [tag] at [severity], with an optional [throwable]. */
    public fun log(severity: Severity, tag: String, message: String, throwable: Throwable?)
}

/** Logs [message] at [Severity.DEBUG]. */
public inline fun Logger.d(tag: String, message: Any?) {
    log(severity = Severity.DEBUG, tag = tag, message = message.toString(), throwable = null)
}

/** Logs [message] and [throwable] at [Severity.DEBUG]. */
public inline fun Logger.d(tag: String, message: Any?, throwable: Throwable) {
    log(severity = Severity.DEBUG, tag = tag, message = message.toString(), throwable = throwable)
}

/** Logs [message] at [Severity.INFO]. */
public inline fun Logger.i(tag: String, message: Any?) {
    log(severity = Severity.INFO, tag = tag, message = message.toString(), throwable = null)
}

/** Logs [message] and [throwable] at [Severity.INFO]. */
public inline fun Logger.i(tag: String, message: Any?, throwable: Throwable) {
    log(severity = Severity.INFO, tag = tag, message = message.toString(), throwable = throwable)
}

/** Logs [message] at [Severity.WARN]. */
public inline fun Logger.w(tag: String, message: Any?) {
    log(severity = Severity.WARN, tag = tag, message = message.toString(), throwable = null)
}

/** Logs [message] and [throwable] at [Severity.WARN]. */
public inline fun Logger.w(tag: String, message: Any?, throwable: Throwable) {
    log(severity = Severity.WARN, tag = tag, message = message.toString(), throwable = throwable)
}

/** Logs [message] at [Severity.ERROR]. */
public inline fun Logger.e(tag: String, message: Any?) {
    log(severity = Severity.ERROR, tag = tag, message = message.toString(), throwable = null)
}

/** Logs [message] and [throwable] at [Severity.ERROR]. */
public inline fun Logger.e(tag: String, message: Any?, throwable: Throwable) {
    log(severity = Severity.ERROR, tag = tag, message = message.toString(), throwable = throwable)
}
