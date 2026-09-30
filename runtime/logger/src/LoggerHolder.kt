// TooManyFunctions: one function per severity and one overload per argument shape: a message, a message with a
// throwable, a lazy message. It is a table rather than a set of responsibilities; splitting it would separate `w`
// from `e`.
@file:Suppress("NOTHING_TO_INLINE", "TooManyFunctions")

package tech.annexflow.lvm.logger

/** A class that logs through [logger]. Its `d`, `i`, `w` and `e` extensions tag messages with the class name. */
public interface LoggerHolder {
    public val logger: Logger
}

/** Logs [message] at [severity], tagged with this class's simple name unless [tag] is given. */
public inline fun LoggerHolder.log(
    tag: String = this::class.simpleName.orEmpty(),
    throwable: Throwable? = null,
    severity: Severity,
    message: Any?,
) {
    logger.log(
        tag = tag,
        severity = severity,
        throwable = throwable,
        message = message.toString(),
    )
}

/** Logs the result of [message] at [severity], tagged with this class's simple name unless [tag] is given. */
public inline fun LoggerHolder.log(
    tag: String = this::class.simpleName.orEmpty(),
    throwable: Throwable? = null,
    severity: Severity,
    message: () -> Any?,
) {
    log(
        tag = tag,
        severity = severity,
        throwable = throwable,
        message = message(),
    )
}

/** Logs the result of [message] at [Severity.DEBUG], with an optional [throwable]. */
public inline fun LoggerHolder.d(throwable: Throwable? = null, message: () -> Any?) {
    log(severity = Severity.DEBUG, throwable = throwable, message = message)
}

/** Logs [message] at [Severity.DEBUG]. */
public inline fun LoggerHolder.d(message: Any?) {
    log(severity = Severity.DEBUG, message = { message })
}

/** Logs [message] and [throwable] at [Severity.DEBUG]. */
public inline fun LoggerHolder.d(message: Any?, throwable: Throwable?) {
    log(severity = Severity.DEBUG, throwable = throwable, message = { message })
}

/** Logs the result of [message] at [Severity.INFO], with an optional [throwable]. */
public inline fun LoggerHolder.i(throwable: Throwable? = null, message: () -> Any?) {
    log(severity = Severity.INFO, throwable = throwable, message = message)
}

/** Logs [message] at [Severity.INFO]. */
public inline fun LoggerHolder.i(message: Any?) {
    log(severity = Severity.INFO, message = { message })
}

/** Logs [message] and [throwable] at [Severity.INFO]. */
public inline fun LoggerHolder.i(message: Any?, throwable: Throwable?) {
    log(severity = Severity.INFO, throwable = throwable, message = { message })
}

/** Logs [message] at [Severity.WARN]. */
public inline fun LoggerHolder.w(message: Any?) {
    log(severity = Severity.WARN, message = { message })
}

/** Logs [message] and [throwable] at [Severity.WARN]. */
public inline fun LoggerHolder.w(message: Any?, throwable: Throwable?) {
    log(severity = Severity.WARN, throwable = throwable, message = { message })
}

/** Logs [message] at [Severity.ERROR]. */
public inline fun LoggerHolder.e(message: Any?) {
    log(severity = Severity.ERROR, message = { message })
}

/** Logs [message] and [throwable] at [Severity.ERROR]. */
public inline fun LoggerHolder.e(message: Any?, throwable: Throwable?) {
    log(severity = Severity.ERROR, throwable = throwable, message = { message })
}
