package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Same as [mapState]: a [StateFlow] of [transform] applied to the value of [flow]. */
public fun <T1, R> combineState(
    flow: StateFlow<T1>,
    transform: (a: T1) -> R,
): StateFlow<R> = flow.mapState(transform)

/** A [StateFlow] of [transform] applied to the values of [flow] and [flow2]. See [mapState] for how it behaves. */
public fun <T1, T2, R> combineState(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    transform: (a: T1, b: T2) -> R,
): StateFlow<R> = DerivedStateFlow(
    getValue = { transform(flow.value, flow2.value) },
    flow = combine(flow, flow2) { a, b -> transform(a, b) },
)

/** A [StateFlow] of [transform] applied to the values of three flows. See [mapState] for how it behaves. */
public fun <T1, T2, T3, R> combineState(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    transform: (a: T1, b: T2, c: T3) -> R,
): StateFlow<R> = DerivedStateFlow(
    getValue = { transform(flow.value, flow2.value, flow3.value) },
    flow = combine(flow, flow2, flow3) { a, b, c -> transform(a, b, c) },
)

/** A [StateFlow] of [transform] applied to the values of four flows. See [mapState] for how it behaves. */
public fun <T1, T2, T3, T4, R> combineState(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    flow4: StateFlow<T4>,
    transform: (a: T1, b: T2, c: T3, d: T4) -> R,
): StateFlow<R> = DerivedStateFlow(
    getValue = { transform(flow.value, flow2.value, flow3.value, flow4.value) },
    flow = combine(flow, flow2, flow3, flow4) { a, b, c, d -> transform(a, b, c, d) },
)

// Five flows and a transform are the shape of `combine` itself; the list gets shorter only by taking a source away
// from callers.
/** A [StateFlow] of [transform] applied to the values of five flows. See [mapState] for how it behaves. */
@Suppress("LongParameterList")
public fun <T1, T2, T3, T4, T5, R> combineState(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    flow4: StateFlow<T4>,
    flow5: StateFlow<T5>,
    transform: (a: T1, b: T2, c: T3, d: T4, e: T5) -> R,
): StateFlow<R> = DerivedStateFlow(
    getValue = { transform(flow.value, flow2.value, flow3.value, flow4.value, flow5.value) },
    flow = combine(flow, flow2, flow3, flow4, flow5) { a, b, c, d, e -> transform(a, b, c, d, e) },
)

/**
 * A [StateFlow] of [transform] applied to the value of this flow, without a [kotlinx.coroutines.CoroutineScope].
 *
 * [StateFlow.value] calls [transform] on the current source value at every read, so it is up to date even with no
 * collector. Collectors get the current value and then every distinct change. Keep [transform] cheap and free of side
 * effects: it runs on every read and in every collector.
 */
public fun <T1, R> StateFlow<T1>.mapState(transform: (a: T1) -> R): StateFlow<R> {
    if (this is DerivedStateFlow<T1>) {
        val prevGet = this.getValue
        val upstream = this.flow
        return DerivedStateFlow(
            getValue = { transform(prevGet()) },
            flow = upstream.distinctUntilChanged().map(transform),
        )
    }

    return DerivedStateFlow(
        getValue = { transform(this.value) },
        flow = this.map(transform),
    )
}

/** Emits distinct values only, like every [StateFlow]. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedStateFlow<T>(
    val flow: Flow<T>,
    val getValue: () -> T,
) : StateFlow<T> {

    override val replayCache: List<T>
        get() = listOf(value)

    override val value: T
        get() = getValue()

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        // stateIn stores the values in a StateFlow, which drops equal values, so collectors get distinct values only.
        coroutineScope { flow.stateIn(scope = this).collect(collector) }
    }
}
