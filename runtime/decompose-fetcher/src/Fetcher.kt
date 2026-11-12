package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.stopAtAttempts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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

/** A [Fetcher] on [httpClient], whose configuration usually calls [installFetcherCache]. */
@Suppress("FunctionNaming") // A factory named after the type it creates, like MutableStateFlow().
public fun Fetcher(httpClient: HttpClient): Fetcher = KtorFetcher(httpClient)

/** Ten attempts, a second apart: a network that comes back does so within that time. */
public val Fetcher.Companion.retryPolicy: RetryPolicy<Throwable>
    get() = stopAtAttempts<Throwable>(RETRY_ATTEMPTS) + constantDelay(RETRY_DELAY_MILLIS)

private const val RETRY_ATTEMPTS = 10

private const val RETRY_DELAY_MILLIS = 1000L

private class KtorFetcher(private val httpClient: HttpClient) : Fetcher {

    override suspend fun get(url: String): ResponseBody = ResponseBody(httpClient.get(url))

    @DelicateApi
    override suspend fun clearCacheStorage() {
        httpClient.fetcherCacheStorage?.clear()
    }
}
