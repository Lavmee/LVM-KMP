package tech.annexflow.lvm.decompose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long a shared flow keeps collecting after its last collector leaves, so a screen rotation doesn't restart it. */
private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Owns [componentScope] and launches work in it. Every [AppComponentContext] is one. */
public interface CoroutineScopeOwner {

    /** The scope of the component's work. It is cancelled when the component is destroyed. */
    public val componentScope: CoroutineScope

    /**
     * A [StateFlow] of this flow in [componentScope] that starts with [initialValue]. By default it collects this flow
     * while it has collectors and for five seconds after the last one leaves.
     */
    public fun <T> Flow<T>.stateIn(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    ): StateFlow<T> = stateIn(scope = componentScope, started = started, initialValue = initialValue)

    /**
     * Suspends until this flow emits its first value and returns a [StateFlow] of the flow in [componentScope] that
     * starts with it. The flow is collected once for the first value and again for sharing, as [stateIn] does.
     */
    public suspend fun <T> Flow<T>.stateFlow(
        started: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    ): StateFlow<T> = stateIn(initialValue = first(), started = started)

    /** Launches [block] in [componentScope] on `Dispatchers.Default`, for work that keeps the CPU busy. */
    public fun launchCalculation(block: suspend CoroutineScope.() -> Unit): Job =
        componentScope.launch(context = Dispatchers.Default, block = block)

    /** Launches [block] in [componentScope] on `Dispatchers.IO`, for work that waits on files or the network. */
    public fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
        componentScope.launch(context = Dispatchers.IO, block = block)

    /** Collects this flow in [componentScope]. */
    public fun <T> Flow<T>.launch(): Job = launchIn(componentScope)
}
