package tech.annexflow.lvm.logger.file

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

fun readText(path: Path): String = SystemFileSystem.source(path).buffered().use { it.readString() }

/** Waits until the file at [path] contains [text] and returns its content: the writer runs on its own thread. */
fun awaitText(path: Path, text: String): String = runBlocking {
    withTimeout(30.seconds) {
        var content = ""
        while (text !in content) {
            delay(20.milliseconds)
            if (SystemFileSystem.exists(path)) content = readText(path)
        }
        content
    }
}

fun deleteRecursively(path: Path) {
    if (!SystemFileSystem.exists(path)) return
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).forEach(::deleteRecursively)
    }
    SystemFileSystem.delete(path)
}
