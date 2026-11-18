package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.childContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    @Test
    fun `a flow the root creates is the one every context of the tree reads`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }
        val grandchild = root.childContext("child").childContext("grandchild")

        assertSame(created, grandchild.getScopedStateFlow<Settings>())
        assertSame(created, grandchild.getScopedStateFlowOrNull<Settings>())
    }

    @Test
    fun `the root keeps the class of its flows while components read them by value type`() {
        val root = rootContext()
        // No type arguments: the value types come from the factories and from the types of the properties.
        val settings = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }
        val title = root.getOrCreateScopedStateFlow { MutableStateFlow("title").asStateFlow() }
        val sizes = root.getOrCreateScopedStateFlow { ListStateFlow<Int>() }
        val component = ReaderComponent(root.childContext("child"))

        settings.value = Settings(dark = true)
        sizes.add(1)

        assertSame(settings, component.settings)
        assertSame(title, component.title)
        assertSame(sizes, component.sizes)
        assertEquals(Settings(dark = true), component.settings.value)
        assertEquals(listOf(1), component.sizes.value)
    }

    @Test
    fun `the tree's flow is returned without calling the factory again`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }
        var calls = 0

        val again = root.childContext("child").getOrCreateScopedStateFlow {
            calls++
            MutableStateFlow(Settings())
        }

        assertSame(created, again)
        assertEquals(0, calls)
    }

    @Test
    fun `a flow asked for as a supertype of its class is the tree's flow`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }

        assertSame(created, root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()).asStateFlow() })
    }

    @Test
    fun `a flow asked for as a class the tree's flow is not fails`() {
        val root = rootContext()
        root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()).asStateFlow() }

        assertFailsWith<IllegalStateException> { root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) } }
    }

    @Test
    fun `a missing flow is null from getScopedStateFlowOrNull and fails getScopedStateFlow`() {
        val root = rootContext()
        root.getOrCreateScopedStateFlow { MutableStateFlow("other value type") }

        assertNull(root.getScopedStateFlowOrNull<Settings>())
        assertFailsWith<IllegalArgumentException> { root.getScopedStateFlow<Settings>() }
    }

    @Test
    fun `a flow of nullable values is kept apart from a flow of the same type without null`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow { MutableStateFlow<Settings?>(null) }

        assertSame(created, root.getScopedStateFlow<Settings?>())
        assertNull(root.getScopedStateFlowOrNull<Settings>())
    }

    @Test
    fun `a flow is kept under the value type that its factory returns`() {
        val root = rootContext()
        root.getOrCreateScopedStateFlow { MutableStateFlow(Shape.Circle) }

        assertNotNull(root.getScopedStateFlowOrNull<Shape.Circle>())
        assertNull(root.getScopedStateFlowOrNull<Shape>())
    }

    @Test
    fun `an explicit value type shares a flow of a subtype under that type`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow<Shape, _> { MutableStateFlow(Shape.Circle) }

        assertSame<StateFlow<Shape>>(created, root.getScopedStateFlow<Shape>())
    }

    @Test
    fun `getScoped finds a flow as a StateFlow of its value type`() {
        val root = rootContext()
        val created = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings()) }

        assertSame(created, root.getScoped<StateFlow<Settings>>())
    }

    @Test
    fun `a flow of a type and an instance of that type are kept apart`() {
        val root = rootContext()
        val instance = root.getOrCreateScoped { Settings() }
        val flow = root.getOrCreateScopedStateFlow { MutableStateFlow(Settings(dark = true)) }

        assertSame(instance, root.getScoped<Settings>())
        assertSame(flow, root.getScopedStateFlow<Settings>())
    }
}

private class Counter

private data class Settings(val dark: Boolean = false)

private sealed interface Shape {
    data object Circle : Shape
}

/** A flow of its own class, with a value type that is not its type parameter. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ListStateFlow<E>(
    private val items: MutableStateFlow<List<E>> = MutableStateFlow(emptyList()),
) : StateFlow<List<E>> by items {

    fun add(item: E) {
        items.value += item
    }
}

/** A component that reads the tree's flows the way a component in another module does. */
private class ReaderComponent(context: AppComponentContext) : AppComponentContext by context {
    val settings: StateFlow<Settings> get() = getScopedStateFlow()
    val title: StateFlow<String> get() = getScopedStateFlow()
    val sizes: StateFlow<List<Int>> get() = getScopedStateFlow()
}
