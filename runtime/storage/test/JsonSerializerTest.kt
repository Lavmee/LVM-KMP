package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Buffer

class JsonSerializerTest {

    private val serializer = jsonSerializer { Settings() }

    /** A file written by a newer version of the app still reads. */
    @Test
    fun `keys the value doesn't have are ignored`() = runTest {
        val content = Buffer().writeUtf8("""{"name":"stored","addedLater":true}""")

        assertEquals(Settings(name = "stored"), serializer.readFrom(content))
    }

    /** A default changed in code doesn't change values already stored. */
    @Test
    fun `default values are written`() = runTest {
        val content = Buffer()

        serializer.writeTo(Settings(), content)

        assertContains(content.readUtf8(), "\"volume\"")
    }

    @Test
    fun `maps with keys that are not primitives are stored`() = runTest {
        val shelves = jsonSerializer { Shelf(counts = emptyMap()) }
        val shelf = Shelf(counts = mapOf(Profile(name = "first") to 1, Profile(name = "second", age = 2) to 2))
        val content = Buffer()

        shelves.writeTo(shelf, content)

        assertEquals(shelf, shelves.readFrom(content))
    }

    @Test
    fun `content that isn't the value's JSON is a corruption`() = runTest {
        for (text in listOf("", "not json", """{"volume":"loud"}""")) {
            assertFailsWith<CorruptionException>("content \"$text\"") { serializer.readFrom(Buffer().writeUtf8(text)) }
        }
    }

    @Test
    fun `a Json from the app replaces the default settings`() = runTest {
        val strict = jsonSerializer(Json) { Settings() }
        val content = Buffer().writeUtf8("""{"name":"stored","addedLater":true}""")

        assertFailsWith<CorruptionException> { strict.readFrom(content) }
    }
}
