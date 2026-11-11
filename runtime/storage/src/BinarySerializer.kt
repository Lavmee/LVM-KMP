package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okio.BufferedSink
import okio.BufferedSource
import tech.annexflow.lvm.storage.internal.decodeOrCorrupt

/**
 * An [OkioSerializer] that stores a [T] in [format], such as `ProtoBuf` or `Cbor` from kotlinx-serialization.
 * [defaultValue] produces the value of a store whose file doesn't exist yet. Content that is not a valid [T] fails
 * with a [CorruptionException].
 *
 * The whole file is read into memory before it is decoded.
 */
public inline fun <reified T> binarySerializer(
    format: BinaryFormat,
    noinline defaultValue: () -> T,
): OkioSerializer<T> = binarySerializer(format, format.serializersModule.serializer<T>(), defaultValue)

/**
 * An [OkioSerializer] that stores a value in [format] with [serializer]. [defaultValue] produces the value of a store
 * whose file doesn't exist yet. Content that is not a valid value fails with a [CorruptionException].
 *
 * The whole file is read into memory before it is decoded.
 */
public fun <T> binarySerializer(
    format: BinaryFormat,
    serializer: KSerializer<T>,
    defaultValue: () -> T,
): OkioSerializer<T> = BinaryOkioSerializer(format, serializer, defaultValue)

private class BinaryOkioSerializer<T>(
    private val format: BinaryFormat,
    private val serializer: KSerializer<T>,
    private val produceDefaultValue: () -> T,
) : OkioSerializer<T> {

    override val defaultValue: T get() = produceDefaultValue()

    override suspend fun readFrom(source: BufferedSource): T {
        val bytes = source.readByteArray()
        return decodeOrCorrupt { format.decodeFromByteArray(serializer, bytes) }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.write(format.encodeToByteArray(serializer, t))
    }
}
