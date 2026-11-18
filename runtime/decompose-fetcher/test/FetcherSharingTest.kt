package tech.annexflow.lvm.decompose.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIsNot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class FetcherSharingTest {

    // The server runs on other threads, so it counts with atomic updates.
    private val requests = MutableStateFlow(0)
    private val cancelledRequests = MutableStateFlow(0)

    /** Holds every response back, so that callers can join a running request. */
    private val release = CompletableDeferred<Unit>()

    private var firstRequestFails = false

    /** A server that numbers its responses, so a caller's body tells which request answered it. */
    private val client = HttpClient(
        MockEngine {
            val request = requests.updateAndGet { it + 1 }
            try {
                release.await()
            } catch (e: CancellationException) {
                cancelledRequests.update { it + 1 }
                throw e
            }
            if (firstRequestFails && request == 1) throw IOException("The server is down")
            respond("response $request")
        },
    )

    private val fetcher = Fetcher(client)

    /** Starts [count] gets of [url] and runs them until they wait for the response. */
    private fun TestScope.waitingGets(count: Int, url: String = URL) =
        List(count) { async { fetcher.get(url) } }.also { runCurrent() }

    @Test
    fun `concurrent gets of a url send one request and get its body`() = runTest {
        val callers = waitingGets(2)
        release.complete(Unit)

        val bodies = callers.awaitAll().map { it.body<String>() }

        assertEquals(listOf("response 1", "response 1"), bodies)
        assertEquals(1, requests.value)
    }

    @Test
    fun `concurrent gets of different urls send a request each`() = runTest {
        val callers = waitingGets(1) + waitingGets(1, url = OTHER_URL)
        release.complete(Unit)

        callers.awaitAll()

        assertEquals(2, requests.value)
    }

    @Test
    fun `a get after a request finished sends a new request`() = runTest {
        release.complete(Unit)

        fetcher.get(URL)
        val later = fetcher.get(URL).body<String>()

        assertEquals("response 2", later)
    }

    @Test
    fun `a failed request fails every caller and is not reused`() = runTest {
        firstRequestFails = true
        val callers = List(2) { async { assertFailsWith<IOException> { fetcher.get(URL) } } }
        runCurrent()
        release.complete(Unit)

        callers.awaitAll()

        assertEquals("response 2", fetcher.get(URL).body<String>())
    }

    @Test
    fun `cancelling a caller leaves the request to the other callers`() = runTest {
        val (cancelled, waiting) = waitingGets(2)

        cancelled.cancelAndJoin()
        release.complete(Unit)

        assertEquals("response 1", waiting.await().body<String>())
    }

    @Test
    fun `cancelling every caller cancels the request`() = runTest {
        val callers = waitingGets(2)
        requests.first { it > 0 }

        callers.forEach { it.cancelAndJoin() }

        cancelledRequests.first { it > 0 }
    }

    @Test
    fun `closing the client cancels the requests and fails their callers`() = runTest {
        val caller = async { assertFailsWith<IllegalStateException> { fetcher.get(URL) } }
        runCurrent()
        requests.first { it > 0 }

        client.close()

        // A CancellationException is an IllegalStateException too, but it would end the caller as cancelled.
        assertIsNot<CancellationException>(caller.await())
        cancelledRequests.first { it > 0 }
        assertIsNot<CancellationException>(assertFailsWith<IllegalStateException> { fetcher.get(URL) })
    }

    private companion object {
        const val URL = "https://example.com/rows"
        const val OTHER_URL = "https://example.com/columns"
    }
}
