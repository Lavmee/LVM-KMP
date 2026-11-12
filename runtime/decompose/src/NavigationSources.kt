package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.router.children.NavigationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * A [Flow] of the navigation events of this source, such as a `StackNavigation`. It subscribes when collection starts
 * and gets the events sent after that, and unsubscribes when the collection ends. A slow collector gets the latest
 * event and skips the ones in between.
 */
public fun <T : Any> NavigationSource<T>.asFlow(): Flow<T> = callbackFlow {
    val cancellation = subscribe { event -> trySend(event) }
    awaitClose { cancellation.cancel() }
}.conflate()
