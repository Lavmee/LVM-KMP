package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.stopAtAttempts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.annexflow.lvm.core.DelicateApi

/**
 * Reads data over the network for components. Create one with [Fetcher] on the app's `HttpClient`, provide it to the
 * component tree with [LocalFetcher], and read it with [fetcher].
 */
public interface Fetcher {

    /** Sends a GET request to [url] and returns the response's body. */
    public suspend fun get(url: String): ResponseBody

    /**
     * Deletes every response that [installFetcherCache] stored. Requests in flight finish first. Does nothing when the
     * client has no fetcher cache.
     */
    @DelicateApi
    public suspend fun clearCacheStorage()

    public companion object
}

/**
 * A [Fetcher] on [httpClient], whose configuration usually calls [installFetcherCache].
 *
 * Callers share requests: a [get][Fetcher.get] of a url while a request for it is running waits for that request, and
 * every caller gets the same [ResponseBody]. When the request fails, every caller fails with its exception. A request
 * that finished, failed or was cancelled is not reused, so the next get sends a new one.
 *
 * Requests run on `Dispatchers.IO` in a scope of the fetcher. Cancelling a caller stops its wait, and the request
 * runs on for the other callers; it is cancelled when no caller waits for it. Closing [httpClient] cancels the running
 * requests, and from then on every get fails with an [IllegalStateException].
 */
@Suppress("FunctionNaming") // A factory named after the type it creates, like MutableStateFlow().
public fun Fetcher(httpClient: HttpClient): Fetcher = KtorFetcher(httpClient)

/** Ten attempts, a second apart: a network that comes back does so within that time. */
public val Fetcher.Companion.retryPolicy: RetryPolicy<Throwable>
    get() = stopAtAttempts<Throwable>(RETRY_ATTEMPTS) + constantDelay(RETRY_DELAY_MILLIS)

private const val RETRY_ATTEMPTS = 10

private const val RETRY_DELAY_MILLIS = 1000L

private class KtorFetcher(private val httpClient: HttpClient) : Fetcher {

    // Not a child of the client's job: a child that stays active keeps that job from completing after close(), and
    // an engine that the client closes waits for it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()

    /** The requests that callers wait for, by url. Guarded by [mutex]. */
    private val requests = HashMap<String, SharedRequest>()

    init {
        // close() completes the client's job, and cancelling the client's scope cancels it.
        httpClient.coroutineContext.job.invokeOnCompletion { scope.cancel() }
    }

    override suspend fun get(url: String): ResponseBody {
        val request = join(url)
        try {
            return request.response.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            // The request was cancelled while this caller was not. Its CancellationException would end the caller
            // as if the caller were cancelled.
            throw IllegalStateException("The request to $url was cancelled, for example because the client closed", e)
        } finally {
            withContext(NonCancellable) { leave(url, request) }
        }
    }

    /** The running request for [url], or a new one, with this caller counted as one of its waiters. */
    private suspend fun join(url: String): SharedRequest = mutex.withLock {
        val request = requests[url]?.takeIf { it.response.isActive }
            ?: SharedRequest(scope.async { ResponseBody(httpClient.get(url)) }).also { requests[url] = it }
        request.waiters++
        request
    }

    /** Stops counting a waiter of [request], and cancels the request when no caller waits for it. */
    private suspend fun leave(url: String, request: SharedRequest) = mutex.withLock {
        request.waiters--
        if (request.waiters == 0) {
            if (requests[url] === request) requests.remove(url)
            request.response.cancel()
        }
    }

    @DelicateApi
    override suspend fun clearCacheStorage() {
        httpClient.fetcherCacheStorage?.clear()
    }

    private class SharedRequest(val response: Deferred<ResponseBody>) {

        /** The callers that wait for [response]. Guarded by the fetcher's mutex. */
        var waiters = 0
    }
}
