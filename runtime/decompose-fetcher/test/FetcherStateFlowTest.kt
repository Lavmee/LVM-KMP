package tech.annexflow.lvm.decompose.fetcher

import com.github.michaelbull.retry.policy.stopAtAttempts
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import tech.annexflow.lvm.core.Loadable

@OptIn(ExperimentalCoroutinesApi::class)
class FetcherStateFlowTest {

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
    fun `refresh after a failure retries the block until it loads`() = runTest {
        // The first load's three attempts fail, and so does the first attempt of the refresh.
        var offlineAttempts = 4
        val state = TestCoroutineScopeOwner().fetcherResultState(retryPolicy = stopAtAttempts(3)) {
            if (offlineAttempts-- > 0) error("offline")
            "rows"
        }
        runCurrent()
        assertIs<Loadable.Failed>(state.value)

        state.refresh()

        assertEquals(Loadable.Loaded("rows"), state.value)
    }

    @Test
    fun `a refresh keeps the value while it loads and returns when the load is done`() = runTest {
        val responses = Channel<String>(Channel.UNLIMITED)
        val state = TestCoroutineScopeOwner().fetcherResultState(retryPolicy = oneAttempt) { responses.receive() }
        responses.send("old")
        runCurrent()

        val refresh = launch { state.refresh() }
        runCurrent()

        assertEquals(Loadable.Loaded("old"), state.value)
        assertFalse(refresh.isCompleted)
        responses.send("new")
        runCurrent()
        assertEquals(Loadable.Loaded("new"), state.value)
        assertTrue(refresh.isCompleted)
    }

    @Test
    fun `a load that a refresh replaced never overwrites the newer result`() = runTest {
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val responses = ArrayDeque(listOf(first, second))
        val state = TestCoroutineScopeOwner().fetcherState(initialValue = "", retryPolicy = oneAttempt) {
            val response = responses.removeFirst()
            // A block that ignores cancellation still returns its result after the refresh cancelled it.
            withContext(NonCancellable) { response.await() }
        }
        runCurrent()

        launch { state.refresh() }
        runCurrent()
        second.complete("new")
        runCurrent()
        first.complete("old")
        runCurrent()

        assertEquals("new", state.value)
    }

    @Test
    fun `a refresh that a newer refresh replaced returns with the newer result`() = runTest {
        val responses = Channel<String>(Channel.UNLIMITED)
        val state = TestCoroutineScopeOwner().fetcherState(initialValue = "", retryPolicy = oneAttempt) {
            responses.receive()
        }
        responses.send("first")
        runCurrent()

        val older = launch { state.refresh() }
        runCurrent()
        val newer = launch { state.refresh() }
        runCurrent()
        assertFalse(older.isCompleted)
        responses.send("second")
        runCurrent()

        assertEquals("second", state.value)
        assertTrue(older.isCompleted)
        assertTrue(newer.isCompleted)
    }

    @Test
    fun `a load that a refresh cancels is not reported to onError`() = runTest {
        val responses = Channel<String>(Channel.UNLIMITED)
        var errors = 0
        val state = TestCoroutineScopeOwner().fetcherState(
            initialValue = "",
            onError = { errors++; "failed" },
        ) { responses.receive() }
        runCurrent()

        val refresh = launch { state.refresh() }
        runCurrent()
        responses.send("rows")
        refresh.join()

        assertEquals("rows", state.value)
        assertEquals(0, errors)
    }

    @Test
    fun `cancelling the caller of refresh doesn't cancel the load`() = runTest {
        val responses = Channel<String>(Channel.UNLIMITED)
        val state = TestCoroutineScopeOwner().fetcherState(initialValue = "", retryPolicy = oneAttempt) {
            responses.receive()
        }
        responses.send("first")
        runCurrent()

        val caller = launch { state.refresh() }
        runCurrent()
        caller.cancel()
        responses.send("second")
        runCurrent()

        assertEquals("second", state.value)
    }
}
