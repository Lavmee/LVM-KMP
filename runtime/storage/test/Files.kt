package tech.annexflow.lvm.storage

import kotlin.random.Random
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import tech.annexflow.lvm.io.AppDirectories

fun temporaryRoot(): Path = Path(SystemTemporaryDirectory, "lvm-storage-test-${Random.nextLong().toULong()}")

fun writeText(path: Path, text: String) {
    path.parent?.let(SystemFileSystem::createDirectories)
    SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
}

fun deleteRecursively(path: Path) {
    if (!SystemFileSystem.exists(path)) return
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).forEach(::deleteRecursively)
    }
    SystemFileSystem.delete(path)
}

/** [AppDirectories] below [root] that counts the reads of [data]. Unlike the real ones, it creates no directories. */
class TestDirectories(private val root: Path) : AppDirectories {

    var dataReads: Int = 0
        private set

    override val cache: Path get() = Path(root, "cache")

    override val data: Path get() = Path(root, "data").also { dataReads++ }
}
