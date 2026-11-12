package tech.annexflow.lvm.decompose.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemFileSystem
import tech.annexflow.lvm.core.DelicateApi

class FetcherTest {

    private val directory = temporaryDirectory()
    private var offline = false
    private var requests = 0

    /** A server whose responses may be stored but are stale at once, so a cache must ask again while online. */
    private val engine = MockEngine {
        requests++
        respond("rows", headers = headersOf(HttpHeaders.CacheControl, "public, max-age=0"))
    }

    private fun fetcher(): Fetcher =
        Fetcher(HttpClient(engine) { installFetcherCache(directory, isOffline = { offline }) })

    @AfterTest
    fun deleteTheDirectory() {
        deleteRecursively(directory)
    }

    @Test
    fun `a response body is read by type`() = runTest {
        assertEquals("rows", fetcher().get(URL).body<String>())
    }

    @Test
    fun `while offline a stored response is served from the files without the network`() = runTest {
        fetcher().get(URL).body<String>()
        offline = true

        // A new client reads the same directory, as the app does after a restart.
        val offlineBody = fetcher().get(URL).body<String>()

        assertEquals("rows", offlineBody)
        assertEquals(1, requests)
    }

    @Test
    fun `while online a stale response is requested again`() = runTest {
        val fetcher = fetcher()

        fetcher.get(URL).body<String>()
        fetcher.get(URL).body<String>()

        assertEquals(2, requests)
    }

    @OptIn(DelicateApi::class)
    @Test
    fun `clearCacheStorage deletes the stored responses`() = runTest {
        val fetcher = fetcher()
        fetcher.get(URL).body<String>()
        assertTrue(SystemFileSystem.list(directory).isNotEmpty())

        fetcher.clearCacheStorage()

        assertTrue(SystemFileSystem.list(directory).isEmpty())
    }

    @OptIn(DelicateApi::class)
    @Test
    fun `clearCacheStorage does nothing on a client without the fetcher cache`() = runTest {
        Fetcher(HttpClient(engine)).clearCacheStorage()
    }

    private companion object {
        const val URL = "https://example.com/rows"
    }
}
