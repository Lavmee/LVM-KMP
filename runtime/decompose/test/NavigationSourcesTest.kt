package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.router.children.NavigationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSourcesTest {

    @Test
    fun `asFlow emits the events sent while it is collected and then unsubscribes`() = runTest {
        val source = EventSource()
        val received = async { source.asFlow().first() }
        runCurrent()

        source.send("open")

        assertEquals("open", received.await())
        assertEquals(0, source.observers.size)
    }
}

/** A navigation source that sends events to its observers at once. */
private class EventSource : NavigationSource<String> {
    val observers = mutableListOf<(String) -> Unit>()

    fun send(event: String) {
        observers.toList().forEach { it(event) }
    }

    override fun subscribe(observer: (String) -> Unit): Cancellation {
        observers += observer
        return Cancellation { observers -= observer }
    }
}
