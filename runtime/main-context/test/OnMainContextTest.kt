package tech.annexflow.lvm.maincontext

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class, MainContextCompilerApi::class)
class OnMainContextTest {

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onMainContext runs the block on the immediate main dispatcher`() = runTest {
        val main = FakeMainDispatcher(isImmediate = false)
        Dispatchers.setMain(main)

        val dispatcher = onMainContext { currentCoroutineContext()[ContinuationInterceptor] }

        assertSame(main.immediate, dispatcher)
    }
}

/** Runs every block at once, so the test sees which of the two dispatchers ran it. */
private class FakeMainDispatcher(private val isImmediate: Boolean) : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = if (isImmediate) this else FakeMainDispatcher(isImmediate = true)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !isImmediate

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
