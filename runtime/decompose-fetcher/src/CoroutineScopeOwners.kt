package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.coroutines.runSuspendCatching
import tech.annexflow.lvm.decompose.CoroutineScopeOwner

/**
 * A [FetcherStateFlow] that starts with [initialValue] and then holds the result of [block], or [onError] of the last
 * failure. It loads when it is created, on [FetcherStateFlow.refresh] and on every emission of [refreshOn].
 *
 * [block] runs on `Dispatchers.IO` in `componentScope`, and [retryPolicy] retries it; by default ten times, a second
 * apart. A refresh keeps the value until its load is done, then sets the result or [onError] of the failure.
 * Cancellation, for example when the component is destroyed or a newer refresh replaces the load, is not a failure:
 * [onError] is not called.
 *
 * [refreshOn] is collected in `componentScope` until the component is destroyed, and each emission cancels the load
 * that is running and starts a new one. A `StateFlow` emits its current value first; `drop(1)` skips it.
 */
public fun <T> CoroutineScopeOwner.fetcherState(
    initialValue: T,
    retryPolicy: RetryPolicy<Throwable> = Fetcher.retryPolicy,
    onError: (Throwable) -> T = { initialValue },
    refreshOn: Flow<*> = emptyFlow<Nothing>(),
    block: suspend () -> T,
): FetcherStateFlow<T> {
    val state = FetcherStateFlowImpl(initialValue = initialValue, owner = this) {
        runSuspendCatching { retry(retryPolicy) { block() } }.getOrElse(onError)
    }
    launchIO { state.refresh() }
    refreshOnEach(trigger = refreshOn, state = state) { true }
    return state
}

/**
 * A [FetcherStateFlow] of [block]'s result as a [Loadable]: [Loadable.Loading], then [Loadable.Loaded] or
 * [Loadable.Failed]. A refresh keeps the current state until its load is done, so the state doesn't go back to
 * [Loadable.Loading]. See [fetcherState] for [retryPolicy] and [refreshOn].
 *
 * [retryOn] refreshes the state on each emission while the state is [Loadable.Failed]; an emission while it is
 * [Loadable.Loading] or [Loadable.Loaded] does nothing. It must emit events that can make a failed load succeed, such
 * as the connection coming back, not a state such as "is connected": `distinctUntilChanged().drop(1).filter { it }`
 * turns a `Flow<Boolean>` of the connection into such events. A flow of a state emits when nothing has recovered, and
 * one that depends on this state spins: a load that fails while the device stays online fails again on refresh, each
 * failure is a new [Loadable.Failed] with a new throwable, the flow emits again, and the state reloads as fast as the
 * network answers.
 */
public fun <T> CoroutineScopeOwner.fetcherResultState(
    retryPolicy: RetryPolicy<Throwable> = Fetcher.retryPolicy,
    refreshOn: Flow<*> = emptyFlow<Nothing>(),
    retryOn: Flow<*> = emptyFlow<Nothing>(),
    block: suspend () -> T,
): FetcherStateFlow<Loadable<T>> {
    val state = fetcherState<Loadable<T>>(
        initialValue = Loadable.Loading,
        retryPolicy = retryPolicy,
        onError = { error -> Loadable.Failed(error) },
        refreshOn = refreshOn,
        block = { Loadable.Loaded(block()) },
    )
    refreshOnEach(trigger = retryOn, state = state) { state.value is Loadable.Failed }
    return state
}

/**
 * Refreshes [state] on each emission of [trigger] for which [condition] returns `true`. The collector doesn't wait for
 * a refresh to finish before it takes the next emission, so an emission during a load restarts it.
 */
private fun CoroutineScopeOwner.refreshOnEach(trigger: Flow<*>, state: FetcherStateFlow<*>, condition: () -> Boolean) {
    componentScope.launch {
        trigger.collectLatest { if (condition()) state.refresh() }
    }
}
