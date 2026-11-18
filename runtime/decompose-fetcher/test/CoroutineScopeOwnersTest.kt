package tech.annexflow.lvm.decompose.fetcher

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.github.michaelbull.retry.policy.stopAtAttempts
import com.github.michaelbull.retry.retry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.decompose.PreviewAppComponentContext

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineScopeOwnersTest {

    private val threeAttempts = stopAtAttempts<Throwable>(3)

    private val oneAttempt = stopAtAttempts<Throwable>(1)

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetcherState retries the block until it succeeds`() = runTest {
        var attempts = 0
        val state = PreviewAppComponentContext().fetcherState(initialValue = "", retryPolicy = threeAttempts) {
            attempts++
            if (attempts < 3) error("offline")
            "rows"
        }

        assertEquals("rows", state.first { it.isNotEmpty() })
        assertEquals(3, attempts)
    }

    @Test
    fun `fetcherState falls back to onError after the last attempt`() = runTest {
        val state = PreviewAppComponentContext().fetcherState(
            initialValue = "",
            retryPolicy = threeAttempts,
            onError = { error -> "failed: ${error.message}" },
        ) { error("offline") }

        assertEquals("failed: offline", state.first { it.isNotEmpty() })
    }

    @Test
    fun `a cancelled fetcherState keeps its value and doesn't call onError`() = runTest {
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = PreviewAppComponentContext(lifecycle = lifecycle)
        var errors = 0
        val state = context.fetcherState(initialValue = "", onError = { errors++; "failed" }) { awaitCancellation() }

        lifecycle.destroy()
        context.componentScope.coroutineContext.job.join()

        assertEquals("", state.value)
        assertEquals(0, errors)
    }

    @Test
    fun `fetcherResultState is Loading and then Loaded`() = runTest {
        // The block runs on Dispatchers.IO, so it waits for the first check instead of racing it.
        val checkedLoading = CompletableDeferred<Unit>()
        val state = PreviewAppComponentContext().fetcherResultState(retryPolicy = threeAttempts) {
            checkedLoading.await()
            "rows"
        }

        assertEquals(Loadable.Loading, state.value)
        checkedLoading.complete(Unit)
        assertEquals(Loadable.Loaded("rows"), state.first { it != Loadable.Loading })
    }

    @Test
    fun `fetcherResultState is Failed after the last attempt`() = runTest {
        val state = PreviewAppComponentContext().fetcherResultState<String>(retryPolicy = threeAttempts) {
            error("offline")
        }

        val failed = state.first { it != Loadable.Loading }

        assertEquals("offline", (failed as Loadable.Failed).throwable.message)
    }

    @Test
    fun `refreshOn refreshes the state on every emission`() = runTest {
        val trigger = MutableSharedFlow<Unit>()
        var loads = 0
        val state = TestCoroutineScopeOwner().fetcherState(initialValue = 0, refreshOn = trigger) { ++loads }
        runCurrent()

        trigger.emit(Unit)
        runCurrent()
        trigger.emit(Unit)
        runCurrent()

        assertEquals(3, state.value)
    }

    @Test
    fun `an emission of refreshOn restarts a load that an earlier emission started`() = runTest {
        // The buffer keeps emit from waiting for a collector that is still busy with the earlier emission.
        val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var loads = 0
        TestCoroutineScopeOwner().fetcherState(initialValue = "", refreshOn = trigger) {
            loads++
            awaitCancellation()
        }
        runCurrent()

        trigger.emit(Unit)
        runCurrent()
        trigger.emit(Unit)
        runCurrent()

        assertEquals(3, loads)
    }

    @Test
    fun `retryOn refreshes a failed state`() = runTest {
        val reconnected = MutableSharedFlow<Unit>()
        var online = false
        val state = TestCoroutineScopeOwner().fetcherResultState(retryPolicy = oneAttempt, retryOn = reconnected) {
            if (!online) error("offline")
            "rows"
        }
        runCurrent()
        assertIs<Loadable.Failed>(state.value)

        online = true
        reconnected.emit(Unit)
        runCurrent()

        assertEquals(Loadable.Loaded("rows"), state.value)
    }

    @Test
    fun `retryOn ignores emissions while the state is loading or loaded`() = runTest {
        val reconnected = MutableSharedFlow<Unit>()
        val responses = Channel<String>(Channel.UNLIMITED)
        var loads = 0
        val state = TestCoroutineScopeOwner().fetcherResultState(retryOn = reconnected) {
            loads++
            responses.receive()
        }
        runCurrent()

        reconnected.emit(Unit)
        runCurrent()
        responses.send("rows")
        runCurrent()
        reconnected.emit(Unit)
        runCurrent()

        assertEquals(Loadable.Loaded("rows"), state.value)
        assertEquals(1, loads)
    }

    @Test
    fun `destroying the component cancels the load and stops the triggers`() = runTest {
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val trigger = MutableSharedFlow<Unit>()
        var loads = 0
        var cancelledLoads = 0
        val state = TestCoroutineScopeOwner(lifecycle).fetcherResultState<String>(
            refreshOn = trigger,
            retryOn = trigger,
        ) {
            loads++
            try {
                awaitCancellation()
            } finally {
                cancelledLoads++
            }
        }
        runCurrent()
        assertEquals(2, trigger.subscriptionCount.value)

        lifecycle.destroy()
        runCurrent()
        state.refresh()

        assertEquals(0, trigger.subscriptionCount.value)
        assertEquals(1, loads)
        assertEquals(1, cancelledLoads)
        assertEquals(Loadable.Loading, state.value)
    }

    @Test
    fun `the default retry policy tries ten times a second apart`() = runTest {
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            retry(Fetcher.retryPolicy) {
                attempts++
                error("offline")
            }
        }

        assertEquals(10, attempts)
        assertEquals(9_000, currentTime)
    }
}
