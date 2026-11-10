package tech.annexflow.lvm.io.internal

import kotlinx.io.files.Path

internal data class AppDirectoryPaths(val cache: Path, val data: Path)

/**
 * Where the app named [appId] keeps its cache and data on the operating system named [osName]. [home] is the user's
 * home directory and [environment] looks up an environment variable.
 */
internal fun appDirectoryPaths(
    appId: String,
    osName: String,
    home: String,
    environment: (String) -> String?,
): AppDirectoryPaths {
    require(appId.isNotBlank() && '/' !in appId && '\\' !in appId && appId != "." && appId != "..") {
        "appId must be a single folder name, such as tech.example.app, but was \"$appId\""
    }
    return when {
        osName.startsWith("Mac") -> AppDirectoryPaths(
            cache = Path(home, "Library", "Caches", appId),
            data = Path(home, "Library", "Application Support", appId),
        )
        osName.startsWith("Windows") -> AppDirectoryPaths(
            cache = Path(nonEmpty(environment("LOCALAPPDATA")) ?: Path(home, "AppData", "Local"), appId, "Cache"),
            data = Path(nonEmpty(environment("APPDATA")) ?: Path(home, "AppData", "Roaming"), appId),
        )
        else -> AppDirectoryPaths(
            cache = Path(absolute(environment("XDG_CACHE_HOME")) ?: Path(home, ".cache"), appId),
            data = Path(absolute(environment("XDG_DATA_HOME")) ?: Path(home, ".local", "share"), appId),
        )
    }
}

private fun nonEmpty(value: String?): Path? = value?.takeIf { it.isNotEmpty() }?.let { Path(it) }

// The XDG Base Directory specification says to ignore a variable that holds a relative path.
private fun absolute(value: String?): Path? = value?.takeIf { it.startsWith("/") }?.let { Path(it) }
