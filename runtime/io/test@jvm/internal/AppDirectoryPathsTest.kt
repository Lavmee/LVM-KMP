package tech.annexflow.lvm.io.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.io.files.Path

/**
 * Expected paths are built with [Path] like the real ones, so the separator is the host's and the tests pass on any
 * operating system; what they check is the base directory and the folders below it.
 */
class AppDirectoryPathsTest {

    @Test
    fun `macOS keeps the cache in Library Caches and the data in Application Support`() {
        val paths = appDirectoryPaths(APP_ID, osName = "Mac OS X", home = "/Users/me", environment = { null })

        assertEquals(Path("/Users/me", "Library", "Caches", APP_ID), paths.cache)
        assertEquals(Path("/Users/me", "Library", "Application Support", APP_ID), paths.data)
    }

    @Test
    fun `Windows keeps the cache in the local app data and the data in the roaming app data`() {
        val env = mapOf("LOCALAPPDATA" to "C:\\Local", "APPDATA" to "C:\\Roaming")

        val paths = appDirectoryPaths(APP_ID, osName = "Windows 11", home = "C:\\Users\\me", environment = env::get)

        assertEquals(Path("C:\\Local", APP_ID, "Cache"), paths.cache)
        assertEquals(Path("C:\\Roaming", APP_ID), paths.data)
    }

    @Test
    fun `Windows falls back to the profile folders when the variables are missing`() {
        val paths = appDirectoryPaths(APP_ID, osName = "Windows 11", home = "C:\\Users\\me", environment = { null })

        assertEquals(Path("C:\\Users\\me", "AppData", "Local", APP_ID, "Cache"), paths.cache)
        assertEquals(Path("C:\\Users\\me", "AppData", "Roaming", APP_ID), paths.data)
    }

    @Test
    fun `Linux follows absolute XDG variables`() {
        val env = mapOf("XDG_CACHE_HOME" to "/var/cache/me", "XDG_DATA_HOME" to "/var/data/me")

        val paths = appDirectoryPaths(APP_ID, osName = "Linux", home = "/home/me", environment = env::get)

        assertEquals(Path("/var/cache/me", APP_ID), paths.cache)
        assertEquals(Path("/var/data/me", APP_ID), paths.data)
    }

    @Test
    fun `Linux ignores missing and relative XDG variables`() {
        val env = mapOf("XDG_CACHE_HOME" to "relative/cache")

        val paths = appDirectoryPaths(APP_ID, osName = "Linux", home = "/home/me", environment = env::get)

        assertEquals(Path("/home/me", ".cache", APP_ID), paths.cache)
        assertEquals(Path("/home/me", ".local", "share", APP_ID), paths.data)
    }

    @Test
    fun `an appId that is not a single folder name is rejected`() {
        for (appId in listOf("", " ", "tech/app", "tech\\app", ".", "..")) {
            assertFailsWith<IllegalArgumentException>("appId \"$appId\"") {
                appDirectoryPaths(appId, osName = "Linux", home = "/home/me", environment = { null })
            }
        }
    }
}

private const val APP_ID = "tech.example.app"
