package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.lifecycle.Lifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until this lifecycle is at least [Lifecycle.State.STARTED], or returns at once if it is.
 *
 * Work that ends by opening another screen, such as a share sheet, waits with it until the app is visible again:
 * Android doesn't let a background app start an activity. Call it on the main thread, like every lifecycle call; it
 * unsubscribes when the caller is cancelled.
 */
public suspend fun Lifecycle.awaitStarted() {
    if (state >= Lifecycle.State.STARTED) return

    suspendCancellableCoroutine { continuation ->
        val callbacks = object : Lifecycle.Callbacks {
            override fun onStart() {
                unsubscribe(this)
                continuation.resume(Unit)
            }
        }
        subscribe(callbacks)
        continuation.invokeOnCancellation { unsubscribe(callbacks) }
    }
}
