package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer

@OptIn(ExperimentalSerializationApi::class)
class BinarySerializerTest {

    private val serializer = binarySerializer(ProtoBuf) { Profile(name = "default") }

    @Test
    fun `a value survives a round trip through the format`() = runTest {
        val content = Buffer()

        serializer.writeTo(Profile(name = "stored", age = 42), content)

        assertEquals(Profile(name = "stored", age = 42), serializer.readFrom(content))
    }

    @Test
    fun `bytes that aren't a valid value are a corruption`() = runTest {
        // Profile.name has no default, so a message without it is not a Profile.
        assertFailsWith<CorruptionException> { serializer.readFrom(Buffer()) }
    }
}
