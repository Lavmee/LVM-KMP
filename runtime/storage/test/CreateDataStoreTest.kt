package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.handlers.ReThrowCorruptionHandler
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class CreateDataStoreTest {

    private val root = temporaryRoot()
    private val directories = TestDirectories(root)
    private val file = Path(root, "data", "settings.json")
    private val settingsSerializer = jsonSerializer { Settings() }
    private val jobs = mutableListOf<Job>()

    @AfterTest
    fun closeTheStoresAndDeleteTheRoot() {
        jobs.forEach { it.cancel() }
        // Best effort: a store that is still closing may hold its file.
        runCatching { deleteRecursively(root) }
    }

    /** A store stays open until the Job of its context completes; tests cancel it to open the file again. */
    private fun storeContext(): CoroutineContext = Job().also(jobs::add) + Dispatchers.IO

    private suspend fun closeTheStores() {
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `an update lands in the named file of the data directory and the next store reads it`() = runTest {
        val store = createDataStore(directories, "settings.json", settingsSerializer, context = storeContext())
        store.updateData { it.copy(name = "updated") }
        closeTheStores()

        val next = createDataStore(directories, "settings.json", settingsSerializer, context = storeContext())

        assertTrue(SystemFileSystem.exists(file))
        assertEquals("updated", next.data.first().name)
    }

    /** Apps build stores on the main thread, and reading the data directory is disk work. */
    @Test
    fun `building a store doesn't read the data directory`() = runTest {
        val store = createDataStore(directories, "settings.json", settingsSerializer, context = storeContext())
        assertEquals(0, directories.dataReads)

        store.data.first()

        assertNotEquals(0, directories.dataReads)
    }

    @Test
    fun `a file that can't be decoded is replaced with the default value`() = runTest {
        writeText(file, "not json")
        val store = createDataStore(
            directories,
            "settings.json",
            jsonSerializer { Settings(name = "default") },
            context = storeContext(),
        )

        assertEquals("default", store.data.first().name)
        closeTheStores()

        // A store with another default value and no reset reads the replaced file.
        val next = createDataStore(
            directories,
            "settings.json",
            jsonSerializer { Settings(name = "other default") },
            corruptionHandler = ReThrowCorruptionHandler(),
            context = storeContext(),
        )
        assertEquals("default", next.data.first().name)
    }

    @Test
    fun `a corruption handler from the app replaces the reset`() = runTest {
        writeText(file, "not json")
        val store = createDataStore(
            directories,
            "settings.json",
            settingsSerializer,
            corruptionHandler = ReThrowCorruptionHandler(),
            context = storeContext(),
        )

        assertFailsWith<CorruptionException> { store.data.first() }
    }

    @Test
    fun `migrations run before the first value is read`() = runTest {
        val migration = object : DataMigration<Settings> {
            override suspend fun shouldMigrate(currentData: Settings): Boolean = currentData.name.isEmpty()

            override suspend fun migrate(currentData: Settings): Settings = currentData.copy(name = "migrated")

            override suspend fun cleanUp() = Unit
        }
        val store = createDataStore(
            directories,
            "settings.json",
            settingsSerializer,
            migrations = listOf(migration),
            context = storeContext(),
        )

        assertEquals("migrated", store.data.first().name)
    }

    @Test
    fun `a file name that is not a single file name is rejected`() {
        for (fileName in listOf("", " ", "stores/settings.json", "stores\\settings.json", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("fileName \"$fileName\"") {
                createDataStore(directories, fileName, settingsSerializer, context = storeContext())
            }
        }
    }

    @Test
    fun `a store at a path keeps its file there and creates the missing directories`() = runTest {
        val path = Path(root, "nested", "stores", "settings.json")
        val store = createDataStore(path = { path }, serializer = settingsSerializer, context = storeContext())

        store.updateData { it.copy(name = "updated") }

        assertTrue(SystemFileSystem.exists(path))
    }
}
