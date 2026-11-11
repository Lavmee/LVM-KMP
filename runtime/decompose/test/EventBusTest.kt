package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.childContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class EventBusTest {

    @Test
    fun `components get the bus the root created for their event type`() {
        val root = rootContext()
        val created = root.getOrCreateEventBus<ItemEvent>()
        val child = root.childContext("child")

        assertSame(created, child.eventBus<ItemEvent>())
        assertSame(created, child.getOrCreateEventBus<ItemEvent>())
    }

    @Test
    fun `each event type has its own bus`() {
        val root = rootContext()

        val items = root.getOrCreateEventBus<ItemEvent>()
        val texts = root.getOrCreateEventBus<String>()

        assertNotSame<Any>(items, texts)
        assertFailsWith<IllegalArgumentException> { root.eventBus<Int>() }
    }

    @Test
    fun `an emitted event reaches the collectors`() = runTest {
        val bus = rootContext().getOrCreateEventBus<ItemEvent>()
        // Undispatched, so the collector subscribes before the event is emitted.
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.events.first() }

        bus.emit(ItemEvent.Created)

        assertEquals(ItemEvent.Created, received.await())
    }
}

private enum class ItemEvent { Created }
