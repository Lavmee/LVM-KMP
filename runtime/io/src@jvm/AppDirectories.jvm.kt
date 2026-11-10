package tech.annexflow.lvm.io

import tech.annexflow.lvm.io.internal.PathAppDirectories
import tech.annexflow.lvm.io.internal.appDirectoryPaths

/**
 * The directories of the app named [appId], in the places the operating system sets aside for apps:
 *
 * - macOS: `~/Library/Caches/<appId>` and `~/Library/Application Support/<appId>`
 * - Windows: `%LOCALAPPDATA%\<appId>\Cache` and `%APPDATA%\<appId>`, or the same folders under `~\AppData` when the
 *   variables are missing
 * - Linux and other systems: `$XDG_CACHE_HOME/<appId>` and `$XDG_DATA_HOME/<appId>` when they hold absolute paths,
 *   otherwise `~/.cache/<appId>` and `~/.local/share/<appId>`
 *
 * `~` is the `user.home` system property.
 *
 * @throws IllegalArgumentException if [appId] is not a single folder name: blank, containing `/` or `\`, or `.` or
 * `..`.
 */
public fun AppDirectories(appId: String): AppDirectories {
    val paths = appDirectoryPaths(
        appId = appId,
        osName = System.getProperty("os.name").orEmpty(),
        home = System.getProperty("user.home"),
        environment = System::getenv,
    )
    return PathAppDirectories(cachePath = paths.cache, dataPath = paths.data)
}
