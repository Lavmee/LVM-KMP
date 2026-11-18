package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.RetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.decompose.CoroutineScopeOwner

/**
 * [CoroutineScopeOwner.fetcherState] with the retry policy of the component tree, [LocalFetcherRetryPolicy], unless
 * [retryPolicy] is passed. Everything else works as there.
 *
 * A component is an [AppComponentContext], so a call in a component resolves to this function rather than to the one
 * on [CoroutineScopeOwner]: its receiver type is the more specific one. Code whose receiver is only a
 * [CoroutineScopeOwner] calls the other function and gets [Fetcher.retryPolicy].
 */
public fun <T> AppComponentContext.fetcherState(
    initialValue: T,
    retryPolicy: RetryPolicy<Throwable> = locals[LocalFetcherRetryPolicy],
    onError: (Throwable) -> T = { initialValue },
    refreshOn: Flow<*> = emptyFlow<Nothing>(),
    block: suspend () -> T,
): FetcherStateFlow<T> = newFetcherState(initialValue, retryPolicy, onError, refreshOn, block)

/**
 * [CoroutineScopeOwner.fetcherResultState] with the retry policy of the component tree, [LocalFetcherRetryPolicy],
 * unless [retryPolicy] is passed. Everything else works as there. A call in a component resolves to this function, as
 * for [fetcherState].
 */
public fun <T> AppComponentContext.fetcherResultState(
    retryPolicy: RetryPolicy<Throwable> = locals[LocalFetcherRetryPolicy],
    refreshOn: Flow<*> = emptyFlow<Nothing>(),
    retryOn: Flow<*> = emptyFlow<Nothing>(),
    block: suspend () -> T,
): FetcherStateFlow<Loadable<T>> = newFetcherResultState(retryPolicy, refreshOn, retryOn, block)
