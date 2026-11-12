package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.coroutines.runSuspendCatching
import tech.annexflow.lvm.decompose.CoroutineScopeOwner

/**
 * A [StateFlow] that starts with [initialValue] and then holds the result of [block], or [onError] of the last
 * failure.
 *
 * [block] runs on `Dispatchers.IO` in `componentScope`, and [retryPolicy] retries it; by default ten times, a second
 * apart. Cancellation, for example when the component is destroyed, is not a failure: [onError] is not called.
 */
public inline fun <T> CoroutineScopeOwner.fetcherState(
    initialValue: T,
    retryPolicy: RetryPolicy<Throwable> = Fetcher.retryPolicy,
    crossinline onError: (Throwable) -> T = { initialValue },
    crossinline block: suspend () -> T,
): StateFlow<T> {
    val state = MutableStateFlow(initialValue)
    launchIO {
        runSuspendCatching { retry(retryPolicy) { block() } }
            .onSuccess { result -> state.value = result }
            .onFailure { error -> state.value = onError(error) }
    }
    return state
}

/**
 * A [StateFlow] of [block]'s result as a [Loadable]: [Loadable.Loading], then [Loadable.Loaded] or [Loadable.Failed].
 * See [fetcherState].
 */
public inline fun <T> CoroutineScopeOwner.fetcherResultState(
    retryPolicy: RetryPolicy<Throwable> = Fetcher.retryPolicy,
    crossinline block: suspend () -> T,
): StateFlow<Loadable<T>> = fetcherState(
    initialValue = Loadable.Loading,
    retryPolicy = retryPolicy,
    onError = { error -> Loadable.Failed(error) },
    block = { Loadable.Loaded(block()) },
)
