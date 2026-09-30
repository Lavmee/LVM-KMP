package tech.annexflow.lvm.logger.console.internal

import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.Severity
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity as KermitSeverity

internal fun Severity.asKermit(): KermitSeverity = when (this) {
    Severity.VERBOSE -> KermitSeverity.Verbose
    Severity.DEBUG -> KermitSeverity.Debug
    Severity.INFO -> KermitSeverity.Info
    Severity.WARN -> KermitSeverity.Warn
    Severity.ERROR -> KermitSeverity.Error
    Severity.ASSERT -> KermitSeverity.Assert
}

internal fun KermitLogger.asExternal(): Logger = Logger { severity, tag, message, throwable ->
    this.log(severity = severity.asKermit(), tag = tag, throwable = throwable, message = message)
}
