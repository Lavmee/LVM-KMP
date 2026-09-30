package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map

/** Applies [transform] to every element of every emitted list. */
public inline fun <T, R> Flow<List<T>>.mapList(
    crossinline transform: (T) -> R,
): Flow<List<R>> = this.map { list -> list.map(transform = transform) }

/** Collects this flow with [collector] and returns the outcome as a [Result]. Cancellation is rethrown. */
public suspend inline fun <T> Flow<T>.collectCatching(collector: FlowCollector<T>): Result<Unit> =
    runSuspendCatching { this.collect(collector) }
