package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StateKeepersTest {

    @Test
    fun `a state flow starts with its value and restores the value it had when the state was saved`() {
        val first = StateKeeperDispatcher()
        val state = first.mutableStateFlow(key = "query") { "initial" }
        assertEquals("initial", state.value)
        state.value = "changed"

        val restored = StateKeeperDispatcher(first.save()).mutableStateFlow(key = "query") { "initial" }

        assertEquals("changed", restored.value)
    }

    @Test
    fun `the state of a component survives through its context's state keeper`() {
        val first = StateKeeperDispatcher()
        PreviewAppComponentContext(stateKeeper = first).mutableState { 0 }.value = 5

        val restored = PreviewAppComponentContext(stateKeeper = StateKeeperDispatcher(first.save())).mutableState { 0 }

        assertEquals(5, restored.value)
    }

    @Test
    fun `a component that asks for its state twice fails`() {
        val context = PreviewAppComponentContext()
        context.mutableState { 0 }

        assertFailsWith<IllegalStateException> { context.mutableState { 0 } }
    }
}
