package tech.annexflow.lvm.decompose.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import kotlinx.io.files.Path

/** A day: without a network, the last data the app got is better than an empty screen. */
private const val DEFAULT_MAX_STALE_SECONDS = 86_400

/**
 * Stores the client's responses in [directory] and serves them while [isOffline] returns `true`.
 *
 * It installs Ktor's `HttpCache` with a file storage in [directory]. While [isOffline] returns `true`, every request
 * asks for `only-if-cached, max-stale=<maxStaleSeconds>`: the cache answers with a stored response up to
 * [maxStaleSeconds] past its expiry, or with `504 Gateway Timeout`, and the network is not used. [isOffline] is
 * called for every request, for example with the state of a connectivity library.
 */
public fun HttpClientConfig<*>.installFetcherCache(
    directory: Path,
    isOffline: () -> Boolean,
    maxStaleSeconds: Int = DEFAULT_MAX_STALE_SECONDS,
) {
    val storage = FileStorage(directory)
    install(HttpCache) { publicStorage(storage) }
    install(OfflineCache) {
        this.storage = storage
        this.isOffline = isOffline
        this.maxStaleSeconds = maxStaleSeconds
    }
}

/** The storage that [installFetcherCache] installed on this client, or `null`. */
internal val HttpClient.fetcherCacheStorage: CacheStorage?
    get() = attributes.getOrNull(FetcherCacheStorageKey)

private val FetcherCacheStorageKey = AttributeKey<CacheStorage>("FetcherCacheStorage")

private class OfflineCacheConfig {
    var storage: CacheStorage? = null
    var isOffline: () -> Boolean = { false }
    var maxStaleSeconds: Int = DEFAULT_MAX_STALE_SECONDS
}

private val OfflineCache = createClientPlugin("FetcherOfflineCache", ::OfflineCacheConfig) {
    pluginConfig.storage?.let { storage -> client.attributes.put(FetcherCacheStorageKey, storage) }
    val isOffline = pluginConfig.isOffline
    val maxStaleSeconds = pluginConfig.maxStaleSeconds

    onRequest { request, _ ->
        if (isOffline()) {
            request.headers.append(HttpHeaders.CacheControl, "only-if-cached, max-stale=$maxStaleSeconds")
        }
    }
}
