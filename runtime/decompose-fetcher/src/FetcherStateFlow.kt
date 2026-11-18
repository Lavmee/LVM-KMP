package tech.annexflow.lvm.decompose.fetcher

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

/**
 * A [StateFlow] of loaded data that [refresh] loads again. Create one with [fetcherState] or [fetcherResultState].
 *
 * Reading and collecting it needs no opt-in. Implementing it is implementing [StateFlow], so it needs the same opt-in
 * to [ExperimentalForInheritanceCoroutinesApi].
 */
@SubclassOptInRequired(ExperimentalForInheritanceCoroutinesApi::class)
public interface FetcherStateFlow<out T> : StateFlow<T> {

    /**
     * Loads the value again and suspends until the load is done.
     *
     * The value stays the same while the load runs, so a screen keeps showing its data, for example during a
     * pull-to-refresh, until the result replaces it.
     *
     * A refresh cancels the load that is running, including the first one, so an older load never overwrites a newer
     * one. When a newer refresh cancels this call's load, this call waits for the newer load instead. Cancelling the
     * caller stops the wait, not the load. After the component is destroyed, it returns at once and the value stays.
     */
    public suspend fun refresh()
}
