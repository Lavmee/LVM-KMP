package tech.annexflow.lvm.decompose

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Passes events of type [E] between components that don't hold each other, for example "an item was created" from a
 * form to the list behind it.
 *
 * An event reaches only the collectors of [events] at the moment it is emitted. A component tree shares one bus per
 * event type: the root creates it with [getOrCreateEventBus], and components get it with [eventBus].
 */
public class EventBus<E> {

    private val mutableEvents = MutableSharedFlow<E>(extraBufferCapacity = 1)

    /** The events emitted from now on. */
    public val events: SharedFlow<E> = mutableEvents.asSharedFlow()

    /** Sends [event] to the current collectors of [events]. Waits while a collector is busy with an earlier event. */
    public suspend fun emit(event: E) {
        mutableEvents.emit(event)
    }
}

/** The tree's bus for events of type [E]. If there is none, it is created. */
public inline fun <reified E> ScopedInstanceKeeperOwner.getOrCreateEventBus(): EventBus<E> =
    getOrCreateScoped { EventBus() }

/**
 * The tree's bus for events of type [E].
 *
 * @throws IllegalArgumentException if the tree has none: create it with [getOrCreateEventBus] in the root.
 */
public inline fun <reified E> ScopedInstanceKeeperOwner.eventBus(): EventBus<E> = getScoped()
