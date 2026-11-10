package tech.annexflow.lvm.io.internal

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

class PathAppDirectoriesTest {

    private val root = Path(SystemTemporaryDirectory, "lvm-io-test-${Random.nextLong().toULong()}")

    @AfterTest
    fun deleteTheRoot() {
        deleteRecursively(root)
    }

    @Test
    fun `reading a directory creates it and building does not`() {
        val directories = PathAppDirectories(cachePath = Path(root, "cache"), dataPath = Path(root, "data"))
        assertFalse(SystemFileSystem.exists(root))

        val cache = directories.cache
        val data = directories.data

        assertEquals(Path(root, "cache"), cache)
        assertEquals(Path(root, "data"), data)
        assertTrue(isDirectory(cache))
        assertTrue(isDirectory(data))
    }

    @Test
    fun `a directory deleted after a read comes back on the next read`() {
        val directories = PathAppDirectories(cachePath = Path(root, "cache"), dataPath = Path(root, "data"))
        SystemFileSystem.delete(directories.cache)

        val cache = directories.cache

        assertTrue(isDirectory(cache))
    }
}

private fun isDirectory(path: Path): Boolean = SystemFileSystem.metadataOrNull(path)?.isDirectory == true

private fun deleteRecursively(path: Path) {
    if (!SystemFileSystem.exists(path)) return
    if (isDirectory(path)) {
        SystemFileSystem.list(path).forEach(::deleteRecursively)
    }
    SystemFileSystem.delete(path)
}
