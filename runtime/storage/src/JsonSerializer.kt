package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.serializer
import okio.BufferedSink
import okio.BufferedSource
import tech.annexflow.lvm.storage.internal.decodeOrCorrupt

/**
 * An [OkioSerializer] that stores a [T] as JSON. [defaultValue] produces the value of a store whose file doesn't exist
 * yet.
 *
 * The JSON settings ignore unknown keys, so a file written by a newer version of the app still reads; encode default
 * values, so changing a default in code doesn't change values already stored; and allow maps with structured keys.
 *
 * Content that is not a valid [T] fails with a [CorruptionException].
 */
public inline fun <reified T> jsonSerializer(noinline defaultValue: () -> T): OkioSerializer<T> =
    jsonSerializer(serializer<T>(), defaultValue = defaultValue)

/**
 * An [OkioSerializer] that stores a [T] as JSON with [json]. [defaultValue] produces the value of a store whose file
 * doesn't exist yet. Content that is not a valid [T] fails with a [CorruptionException].
 */
public inline fun <reified T> jsonSerializer(json: Json, noinline defaultValue: () -> T): OkioSerializer<T> =
    jsonSerializer(json.serializersModule.serializer<T>(), json, defaultValue)

/**
 * An [OkioSerializer] that stores a value as JSON with [serializer] and [json]. [defaultValue] produces the value of a
 * store whose file doesn't exist yet. Content that is not a valid value fails with a [CorruptionException].
 *
 * The default [json] ignores unknown keys, encodes default values and allows maps with structured keys.
 */
public fun <T> jsonSerializer(
    serializer: KSerializer<T>,
    json: Json = StorageJson,
    defaultValue: () -> T,
): OkioSerializer<T> = JsonOkioSerializer(serializer, json, defaultValue)

private val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
}

private class JsonOkioSerializer<T>(
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val produceDefaultValue: () -> T,
) : OkioSerializer<T> {

    override val defaultValue: T get() = produceDefaultValue()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(source: BufferedSource): T =
        decodeOrCorrupt { json.decodeFromBufferedSource(serializer, source) }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: T, sink: BufferedSink) {
        json.encodeToBufferedSink(serializer, t, sink)
    }
}
