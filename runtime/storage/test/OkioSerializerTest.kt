package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReThrowCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIsNot
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import okio.Buffer
import okio.EOFException
import okio.IOException
import okio.Source
import okio.Timeout
import okio.buffer

class OkioSerializerTest {

    private val root = temporaryRoot()
    private val file = Path(root, "profile.bin")
    private val jobs = mutableListOf<Job>()

    @AfterTest
    fun closeTheStoresAndDeleteTheRoot() {
        jobs.forEach { it.cancel() }
        // Best effort: a store that is still closing may hold its file.
        runCatching { deleteRecursively(root) }
    }

    /** A store stays open until the Job of its context completes; tests cancel it to open the file again. */
    private fun storeContext(): CoroutineContext = Job().also(jobs::add) + Dispatchers.IO

    /** Stores a [Profile] in a format of its own: the age in 4 bytes, then the name in UTF-8. */
    private fun profileSerializer(defaultValue: Profile = Profile(name = "default")): OkioSerializer<Profile> =
        okioSerializer(
            defaultValue = { defaultValue },
            read = { source ->
                val age = source.readInt()
                Profile(name = source.readUtf8(), age = age)
            },
            write = { profile, sink -> sink.writeInt(profile.age).writeUtf8(profile.name) },
        )

    @Test
    fun `a value survives a round trip through write and read`() = runTest {
        val serializer = profileSerializer()
        val content = Buffer()

        serializer.writeTo(Profile(name = "stored", age = 42), content)

        assertEquals(Profile(name = "stored", age = 42), serializer.readFrom(content))
    }

    /** Wire reports a truncated message with an EOFException and a missing required field with this one. */
    @Test
    fun `any exception from read is a corruption caused by it`() = runTest {
        for (failure in listOf(EOFException("truncated"), IllegalStateException("missing required field"))) {
            val serializer = okioSerializer(
                defaultValue = { Profile(name = "default") },
                read = { throw failure },
                write = { _, _ -> },
            )

            val corruption = assertFailsWith<CorruptionException>("$failure") { serializer.readFrom(Buffer()) }

            assertSame(failure, corruption.cause)
        }
    }

    /** A corruption would replace a file that a disk error only kept from being read. */
    @Test
    fun `a failure to read the file is not a corruption`() = runTest {
        val failingDisk = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("The disk failed")

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }

        val failure = assertFailsWith<IOException> { profileSerializer().readFrom(failingDisk.buffer()) }

        assertIsNot<CorruptionException>(failure)
    }

    @Test
    fun `cancelling a read is not a corruption`() = runTest {
        val reading = Job()
        val serializer = okioSerializer(
            defaultValue = { Profile(name = "default") },
            read = {
                reading.complete()
                awaitCancellation()
            },
            write = { _, _ -> },
        )
        val read = async { serializer.readFrom(Buffer()) }
        reading.join()

        read.cancel()

        assertFailsWith<CancellationException> { read.await() }
    }

    @Test
    fun `a store replaces a file that read fails on with the default value`() = runTest {
        // Shorter than an age, so read fails with an EOFException.
        writeText(file, "x")
        val store = createDataStore(path = { file }, serializer = profileSerializer(), context = storeContext())

        assertEquals(Profile(name = "default"), store.data.first())
        jobs.forEach { it.cancelAndJoin() }

        // A store with another default value and no reset reads the replaced file.
        val next = createDataStore(
            path = { file },
            serializer = profileSerializer(Profile(name = "other default")),
            corruptionHandler = ReThrowCorruptionHandler(),
            context = storeContext(),
        )
        assertEquals(Profile(name = "default"), next.data.first())
    }
}
