package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Runs [transform] for every element concurrently and returns when all of them have finished.
 *
 * The returned [Deferred]s are complete and keep the order of the elements. If one call fails, the others are
 * cancelled and the failure is rethrown.
 */
public suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R): List<Deferred<R>> = coroutineScope {
    this@mapAsync.map { item -> async { transform(item) } }
}
