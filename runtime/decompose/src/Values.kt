package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * A [Flow] of this value: the current value, then every change. It subscribes to the value when collected and
 * unsubscribes when the collection ends. A slow collector gets the latest value and skips the ones in between.
 */
public fun <T : Any> Value<T>.asFlow(): Flow<T> = callbackFlow {
    val cancellation = subscribe { value -> trySend(value) }
    awaitClose { cancellation.cancel() }
}.conflate()

/**
 * A [StateFlow] of this value. [StateFlow.value] reads the current value at every access; collectors get the current
 * value and then every distinct change.
 */
public fun <T : Any> Value<T>.asStateFlow(): StateFlow<T> = ValueStateFlow(this)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ValueStateFlow<out T : Any>(private val source: Value<T>) : StateFlow<T> {

    override val value: T get() = source.value

    override val replayCache: List<T> get() = listOf(source.value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        val relay = MutableStateFlow(source.value)
        val cancellation = source.subscribe { value -> relay.value = value }
        try {
            relay.collect(collector)
        } finally {
            cancellation.cancel()
        }
    }
}
