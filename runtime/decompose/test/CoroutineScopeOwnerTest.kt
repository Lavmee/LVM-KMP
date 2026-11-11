package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineScopeOwnerTest {

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stateIn stops collecting five seconds after the last collector leaves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = rootContext()
        var collecting = false
        val upstream = MutableStateFlow(1)
            .onStart { collecting = true }
            .onCompletion { collecting = false }
        val state = with(context) { upstream.stateIn(initialValue = 0) }

        val collector = launch { state.collect {} }
        runCurrent()
        assertTrue(collecting)
        collector.cancel()

        advanceTimeBy(4_999)
        assertTrue(collecting)
        advanceTimeBy(2)
        assertEquals(false, collecting)
    }

    @Test
    fun `stateFlow starts with the first value of the flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = rootContext()
        val upstream = flow {
            delay(100)
            emit("first")
            awaitCancellation()
        }

        val state = with(context) { upstream.stateFlow() }

        assertEquals("first", state.value)
    }

    @Test
    fun `work launched off the main thread stops when the component is destroyed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = rootContext(lifecycle)
        val io = context.launchIO { awaitCancellation() }
        val calculation = context.launchCalculation { awaitCancellation() }

        lifecycle.destroy()
        io.join()
        calculation.join()

        assertTrue(io.isCancelled)
        assertTrue(calculation.isCancelled)
    }
}
