package tech.annexflow.lvm.io

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

class AppDirectoriesTest {

    @Test
    fun `cache is Library Caches and data is Library Application Support`() {
        val directories = AppDirectories()

        assertTrue(directories.cache.toString().endsWith("/Library/Caches"), "cache: ${directories.cache}")
        assertTrue(directories.data.toString().endsWith("/Library/Application Support"), "data: ${directories.data}")
    }

    @Test
    fun `the cache and data directories accept new files`() {
        val directories = AppDirectories()

        for (directory in listOf(directories.cache, directories.data)) {
            val file = Path(directory, "lvm-io-test-${Random.nextLong().toULong()}")
            SystemFileSystem.sink(file).buffered().use { it.writeString("test") }
            assertTrue(SystemFileSystem.exists(file), "$file")
            SystemFileSystem.delete(file)
        }
    }
}
