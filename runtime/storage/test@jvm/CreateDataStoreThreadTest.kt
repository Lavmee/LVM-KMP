package tech.annexflow.lvm.storage

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import tech.annexflow.lvm.io.AppDirectories

class CreateDataStoreThreadTest {

    /** Apps read stores from the main thread, and reading the data directory is disk work. */
    @Test
    fun `the data directory is read in the store's context, not the reader's`() {
        val root = temporaryRoot()
        val readOn = AtomicReference<Thread>()
        val directories = object : AppDirectories {
            override val cache: Path get() = Path(root, "cache")
            override val data: Path get() = root.also { readOn.set(Thread.currentThread()) }
        }
        val job = Job()
        val store = createDataStore(
            directories,
            "settings.json",
            jsonSerializer { Settings() },
            context = job + Dispatchers.IO,
        )

        runBlocking { store.data.first() }
        job.cancel()

        assertNotEquals(Thread.currentThread(), assertNotNull(readOn.get()))
        deleteRecursively(root)
    }
}
