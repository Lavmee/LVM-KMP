package tech.annexflow.lvm.logger

/** Keeps every logged message, in order. */
class RecordingLogger : Logger {

    data class Entry(val severity: Severity, val tag: String, val message: String, val throwable: Throwable?)

    val entries: MutableList<Entry> = mutableListOf()

    override fun log(severity: Severity, tag: String, message: String, throwable: Throwable?) {
        entries += Entry(severity, tag, message, throwable)
    }
}
