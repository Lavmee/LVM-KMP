package tech.annexflow.lvm.storage.internal

import androidx.datastore.core.CorruptionException

/**
 * Runs [decode] and turns content it can't decode into a [CorruptionException], which the store passes to its
 * corruption handler. Other errors, such as a failed read, pass through.
 */
internal inline fun <T> decodeOrCorrupt(decode: () -> T): T = try {
    decode()
} catch (e: IllegalArgumentException) {
    // kotlinx-serialization reports invalid content with SerializationException, a subclass, or with this one.
    throw CorruptionException("The file doesn't hold a valid value: ${e.message}", e)
}
