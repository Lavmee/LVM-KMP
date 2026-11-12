package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ValuesTest {

    @Test
    fun `asFlow emits the current value and then every change`() = runTest {
        val value = MutableValue("first")
        val received = mutableListOf<String>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { value.asFlow().toList(received) }
        runCurrent()

        value.value = "second"
        runCurrent()
        collector.cancel()

        assertEquals(listOf("first", "second"), received)
    }

    @Test
    fun `asFlow unsubscribes when the collection ends`() = runTest {
        val value = CountingValue("first")

        value.asFlow().first()

        assertEquals(0, value.subscribers)
    }

    @Test
    fun `asStateFlow reads the current value and replay cache at every access`() {
        val value = MutableValue("first")
        val state = value.asStateFlow()

        value.value = "second"

        assertEquals("second", state.value)
        assertEquals(listOf("second"), state.replayCache)
    }

    @Test
    fun `asStateFlow collectors get the current value and unsubscribe when they end`() = runTest {
        val value = CountingValue("first")

        assertEquals("first", value.asStateFlow().first())
        assertEquals(0, value.subscribers)
    }
}

/** A value that counts its subscribers. */
private class CountingValue<T : Any>(initial: T) : Value<T>() {
    private val source = MutableValue(initial)

    var subscribers = 0
        private set

    override val value: T get() = source.value

    override fun subscribe(observer: (T) -> Unit): Cancellation {
        subscribers++
        val cancellation = source.subscribe(observer)
        return Cancellation {
            subscribers--
            cancellation.cancel()
        }
    }
}
