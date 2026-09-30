package tech.annexflow.lvm.logger

// One early return per shape of the operands: combined on the right, combined on the left, two plain loggers.
// Folding them into one expression would hide three different answers in nested ifs.
/**
 * A [Logger] that sends every message to this logger and then to [other].
 *
 * Adding to a combined logger keeps one flat list, so `a + b + c` calls each logger once, in that order.
 */
@Suppress("ReturnCount")
public operator fun Logger.plus(other: Logger): Logger {
    if (other is CombinedLogger) {
        return CombinedLogger(
            loggers = buildList {
                add(this@plus)
                addAll(other.loggers)
            },
        )
    }

    if (this is CombinedLogger) {
        return CombinedLogger(loggers = this.loggers + other)
    }

    return CombinedLogger(loggers = listOf(this, other))
}

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
