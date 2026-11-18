package tech.annexflow.lvm.decompose.fetcher

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.annexflow.lvm.decompose.CoroutineScopeOwner

/**
 * Runs each [load] with [owner]'s `launchIO`, so the load belongs to the component and not to the caller of
 * [refresh]. Only the latest load writes its result.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class FetcherStateFlowImpl<T>(
    initialValue: T,
    private val owner: CoroutineScopeOwner,
    private val load: suspend () -> T,
) : FetcherStateFlow<T> {

    private val state = MutableStateFlow(initialValue)

    private val mutex = Mutex()

    /** The load that started last. Guarded by [mutex]. */
    private var latestLoad: Job? = null

    override val value: T
        get() = state.value

    override val replayCache: List<T>
        get() = state.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing = state.collect(collector)

    override suspend fun refresh() {
        var current = mutex.withLock {
            latestLoad?.cancel()
            launchLoad().also { latestLoad = it }
        }
        while (true) {
            current.join()
            current = mutex.withLock { latestLoad }.takeUnless { it === current } ?: return
        }
    }

    private fun launchLoad(): Job = owner.launchIO {
        val result = load()
        val thisLoad = coroutineContext.job
        // Cancellation is cooperative: a block that ignores it still returns a result after a newer load started.
        // Checking under the lock that started the newer load keeps that result out.
        mutex.withLock { if (latestLoad === thisLoad) state.value = result }
    }
}
