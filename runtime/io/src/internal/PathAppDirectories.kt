package tech.annexflow.lvm.io.internal

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import tech.annexflow.lvm.io.AppDirectories

/** [AppDirectories] at fixed paths. Every read creates the directory, so one deleted while the app runs comes back. */
internal class PathAppDirectories(
    private val cachePath: Path,
    private val dataPath: Path,
) : AppDirectories {

    override val cache: Path get() = cachePath.also { SystemFileSystem.createDirectories(it) }

    override val data: Path get() = dataPath.also { SystemFileSystem.createDirectories(it) }
}
