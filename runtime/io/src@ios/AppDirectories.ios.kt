package tech.annexflow.lvm.io

import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import tech.annexflow.lvm.io.internal.PathAppDirectories

/**
 * The app's directories: [AppDirectories.cache] is `Library/Caches` and [AppDirectories.data] is
 * `Library/Application Support` in the app's container. A new container has no `Library/Application Support` yet;
 * the first read of [AppDirectories.data] creates it.
 */
public fun AppDirectories(): AppDirectories = PathAppDirectories(
    cachePath = userDirectory(NSCachesDirectory),
    dataPath = userDirectory(NSApplicationSupportDirectory),
)

private fun userDirectory(directory: NSSearchPathDirectory): Path =
    Path(NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String)
