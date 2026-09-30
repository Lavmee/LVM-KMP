@file:Suppress("NOTHING_TO_INLINE")

package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.core.getOrNull
import tech.annexflow.lvm.core.map

/** A [StateFlow] of this flow's states with [transform] applied to loaded data. See [mapState] for how it behaves. */
public inline fun <T, R> StateFlow<Loadable<T>>.mapLoaded(
    crossinline transform: (T) -> R,
): StateFlow<Loadable<R>> = this.mapState { result -> result.map(transform) }

/** A [StateFlow] of the loaded data, or `null` while loading and after a failure. See [mapState] for how it behaves. */
public inline fun <T : Any> StateFlow<Loadable<T>>.unwrap(): StateFlow<T?> =
    this.mapState { result -> result.getOrNull() }

/** Suspends until this flow holds a [Loadable.Loaded] state and returns it. */
public suspend inline fun <T> StateFlow<Loadable<T>>.awaitLoaded(): Loadable.Loaded<T> =
    this.filterIsInstance<Loadable.Loaded<T>>().first()

/** Suspends until this flow holds a [Loadable.Done] state and returns it. */
public suspend inline fun <T> StateFlow<Loadable<T>>.awaitDone(): Loadable.Done<T> =
    this.filterIsInstance<Loadable.Done<T>>().first()
