package tech.annexflow.lvm.decompose.fetcher

import kotlin.random.Random
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

/** A new directory under the system's temporary directory; it doesn't exist yet. */
fun temporaryDirectory(): Path = Path(SystemTemporaryDirectory, "lvm-fetcher-${Random.nextLong().toULong()}")

fun deleteRecursively(path: Path) {
    if (!SystemFileSystem.exists(path)) return
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).forEach(::deleteRecursively)
    }
    SystemFileSystem.delete(path)
}
