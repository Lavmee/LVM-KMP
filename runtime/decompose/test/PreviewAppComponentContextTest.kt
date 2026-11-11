package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.serializer

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewAppComponentContextTest {

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `destroying the lifecycle of a preview context cancels its scope and destroys its shared instances`() {
        Dispatchers.setMain(FakeMainDispatcher())
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = PreviewAppComponentContext(lifecycle = lifecycle)
        val scope = context.componentScope
        val instance = context.scopedInstanceKeeper.getOrCreate(key = "closeable") { Closeable() }

        lifecycle.destroy()

        assertFalse(scope.isActive)
        assertTrue(instance.isDestroyed)
    }

    @Test
    fun `a preview context saves and restores state through the state keeper it was given`() {
        val first = StateKeeperDispatcher()
        PreviewAppComponentContext(stateKeeper = first).stateKeeper
            .register(key = "name", strategy = String.serializer()) { "saved" }

        val restored = PreviewAppComponentContext(stateKeeper = StateKeeperDispatcher(first.save())).stateKeeper
            .consume(key = "name", strategy = String.serializer())

        assertEquals("saved", restored)
    }
}

private class Closeable : InstanceKeeper.Instance {
    var isDestroyed = false
        private set

    override fun onDestroy() {
        isDestroyed = true
    }
}
