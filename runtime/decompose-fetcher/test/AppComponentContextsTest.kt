package tech.annexflow.lvm.decompose.fetcher

import com.arkivanov.decompose.childContext
import com.github.michaelbull.retry.policy.stopAtAttempts
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tech.annexflow.lvm.core.Loadable
import tech.annexflow.lvm.decompose.PreviewAppComponentContext
import tech.annexflow.lvm.decompose.provideLocals
import tech.annexflow.lvm.decompose.provides

@OptIn(ExperimentalCoroutinesApi::class)
class AppComponentContextsTest {

    private val rootWithThreeAttempts =
        PreviewAppComponentContext().provideLocals(LocalFetcherRetryPolicy provides stopAtAttempts(3))

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a component retries with the policy that the root provided`() = runTest {
        var attempts = 0
        val component = TestAppComponentContext(rootWithThreeAttempts.childContext("child"))

        val state = component.fetcherState(initialValue = "", onError = { "failed" }) {
            attempts++
            error("offline")
        }
        advanceUntilIdle()

        assertEquals("failed", state.value)
        assertEquals(3, attempts)
    }

    @Test
    fun `a component without a policy from the root retries ten times a second apart`() = runTest {
        var attempts = 0

        val state = TestAppComponentContext().fetcherResultState<String> {
            attempts++
            error("offline")
        }
        advanceUntilIdle()

        assertIs<Loadable.Failed>(state.value)
        assertEquals(10, attempts)
        assertEquals(9_000, currentTime)
    }

    @Test
    fun `a retryPolicy argument wins over the policy of the tree`() = runTest {
        var attempts = 0

        TestAppComponentContext(rootWithThreeAttempts).fetcherResultState<String>(retryPolicy = stopAtAttempts(1)) {
            attempts++
            error("offline")
        }
        advanceUntilIdle()

        assertEquals(1, attempts)
    }
}
