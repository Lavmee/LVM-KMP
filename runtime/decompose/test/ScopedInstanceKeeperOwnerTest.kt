package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.childContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ScopedInstanceKeeperOwnerTest {

    @Test
    fun `an instance the root creates is the one every context of the tree gets`() {
        val root = rootContext()
        val created = root.getOrCreateScoped { Counter() }
        val grandchild = root.childContext("child").childContext("grandchild")

        assertSame(created, grandchild.getScoped<Counter>())
        assertSame(created, grandchild.getOrCreateScoped { Counter() })
    }

    @Test
    fun `flows of different value types are kept apart`() {
        val root = rootContext()
        val texts: StateFlow<String> = MutableStateFlow("")
        val numbers: StateFlow<Int> = MutableStateFlow(0)

        root.getOrCreateScoped { texts }
        root.getOrCreateScoped { numbers }

        assertSame(texts, root.getScoped<StateFlow<String>>())
        assertSame(numbers, root.getScoped<StateFlow<Int>>())
    }

    @Test
    fun `a value and a flow of that value are kept apart`() {
        val root = rootContext()
        val flow: StateFlow<String> = MutableStateFlow("in a flow")

        root.getOrCreateScoped { "plain" }
        root.getOrCreateScoped { flow }

        assertEquals("plain", root.getScoped<String>())
        assertSame(flow, root.getScoped<StateFlow<String>>())
    }

    @Test
    fun `a missing instance is null from getScopedOrNull and fails getScoped`() {
        val root = rootContext()

        assertNull(root.getScopedOrNull<Counter>())
        assertFailsWith<IllegalArgumentException> { root.getScoped<Counter>() }
    }

    @Test
    fun `instances under explicit keys are kept apart`() {
        val root = rootContext()
        val first = root.getOrCreateScoped(key = "first") { Counter() }
        val second = root.getOrCreateScoped(key = "second") { Counter() }

        assertSame(first, root.getScoped<Counter>(key = "first"))
        assertSame(second, root.getScoped<Counter>(key = "second"))
        assertNull(root.getScopedOrNull<Counter>())
    }
}

private class Counter
