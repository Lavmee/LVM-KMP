package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.statekeeper.StateKeeper
import com.arkivanov.essenty.statekeeper.StateKeeperOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.serializer

/** The key of [mutableState]. */
@PublishedApi
internal const val COMPONENT_STATE_KEY: String = "ComponentMutableState"

/**
 * A [MutableStateFlow] that keeps its value across process death: it starts with the value saved under [key], or with
 * [value] when there is none, and its current value is saved under [key] with the state of the component. [T] must be
 * serializable, for example a `@Serializable` class.
 *
 * @throws IllegalStateException if [key] is already registered in this state keeper.
 */
public inline fun <reified T : Any> StateKeeper.mutableStateFlow(key: String, value: () -> T): MutableStateFlow<T> {
    val serializer = serializer<T>()
    val flow = MutableStateFlow(consume(key = key, strategy = serializer) ?: value())
    register(key = key, strategy = serializer) { flow.value }
    return flow
}

/** A [MutableStateFlow] that keeps its value under [key] across process death. See [StateKeeper.mutableStateFlow]. */
public inline fun <reified T : Any> StateKeeperOwner.mutableStateFlow(
    key: String,
    value: () -> T,
): MutableStateFlow<T> = stateKeeper.mutableStateFlow(key, value)

/**
 * The state of a component that has one state object, kept across process death under a fixed key. See
 * [StateKeeper.mutableStateFlow].
 *
 * @throws IllegalStateException if the component already called it.
 */
public inline fun <reified T : Any> StateKeeperOwner.mutableState(value: () -> T): MutableStateFlow<T> =
    stateKeeper.mutableStateFlow(COMPONENT_STATE_KEY, value)
