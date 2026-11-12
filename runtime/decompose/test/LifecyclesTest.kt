package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.start
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class LifecyclesTest {

    @Test
    fun `awaitStarted returns at once when the lifecycle is started`() = runTest {
        LifecycleRegistry(Lifecycle.State.STARTED).awaitStarted()
    }

    @Test
    fun `awaitStarted waits until the lifecycle starts and then unsubscribes`() = runTest {
        val lifecycle = CountingLifecycle(LifecycleRegistry(Lifecycle.State.CREATED))
        var started = false
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            lifecycle.awaitStarted()
            started = true
        }
        assertFalse(started)

        lifecycle.registry.start()
        waiter.join()

        assertTrue(started)
        assertEquals(0, lifecycle.subscribers)
    }

    @Test
    fun `a cancelled awaitStarted unsubscribes`() = runTest {
        val lifecycle = CountingLifecycle(LifecycleRegistry(Lifecycle.State.CREATED))
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { lifecycle.awaitStarted() }

        waiter.cancel()
        waiter.join()

        assertEquals(0, lifecycle.subscribers)
    }
}

/** A lifecycle that counts its subscribers. */
private class CountingLifecycle(val registry: LifecycleRegistry) : Lifecycle by registry {
    var subscribers = 0
        private set

    override fun subscribe(callbacks: Lifecycle.Callbacks) {
        subscribers++
        registry.subscribe(callbacks)
    }

    override fun unsubscribe(callbacks: Lifecycle.Callbacks) {
        subscribers--
        registry.unsubscribe(callbacks)
    }
}
