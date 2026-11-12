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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.decompose.PreviewAppComponentContext

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineScopeOwnersTest {

    private val threeAttempts = stopAtAttempts<Throwable>(3)

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
        val state = PreviewAppComponentContext().fetcherResultState(retryPolicy = threeAttempts) { "rows" }

        assertEquals(Loadable.Loading, state.value)
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
