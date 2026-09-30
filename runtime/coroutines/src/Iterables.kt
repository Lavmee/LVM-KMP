package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Runs [transform] for every element concurrently and returns the results in the order of the elements.
 *
 * If one call fails, the others are cancelled and the failure is rethrown.
 */
public suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R): List<R> = coroutineScope {
    this@mapAsync.map { item -> async { transform(item) } }.awaitAll()
}
