package tech.annexflow.lvm.storage

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import tech.annexflow.lvm.io.AppDirectories

class AppDirectoriesDataStoreTest {

    /** The data directory is Library/Application Support, a path with a space. */
    @Test
    fun `a store on the app's directories keeps its value in the data directory`() = runTest {
        val directories = AppDirectories()
        val fileName = "lvm-storage-test-${Random.nextLong().toULong()}.json"
        val file = Path(directories.data, fileName)
        val serializer = jsonSerializer { Settings() }
        try {
            val first = Job()
            createDataStore(directories, fileName, serializer, context = first + Dispatchers.IO)
                .updateData { it.copy(name = "stored") }
            first.cancelAndJoin()

            val next = Job()
            val stored = createDataStore(directories, fileName, serializer, context = next + Dispatchers.IO)
                .data.first()
            next.cancelAndJoin()

            assertTrue(SystemFileSystem.exists(file))
            assertEquals("stored", stored.name)
        } finally {
            if (SystemFileSystem.exists(file)) SystemFileSystem.delete(file)
        }
    }
}
