package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackEvent
import com.arkivanov.essenty.backhandler.BackHandler

/**
 * A [BackHandler] that receives the back gestures of the window this composition is in: the start, progress and
 * cancellation of a predictive back gesture, and back itself.
 *
 * A component's back handler gets the gestures through the root of its component tree. A dialog or a bottom sheet
 * takes the gestures of its window first and closes on back, so the components shown in it never see them.
 * Callbacks registered here from the content of a dialog, for example with [BackHandler], come before the dialog: back
 * reaches them while one of them is enabled, and the dialog when none is.
 *
 * It is connected to Compose's navigation event dispatcher, which on Android is the one of the window's
 * `OnBackPressedDispatcher`, such as a `ComponentDialog`'s.
 *
 * @throws IllegalStateException if the composition has no `NavigationEventDispatcherOwner`.
 */
@Composable
public fun rememberWindowBackHandler(): BackHandler {
    val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "No NavigationEventDispatcherOwner: call rememberWindowBackHandler() inside a Compose window or dialog."
    }
    val dispatcher = remember { BackDispatcher() }

    DisposableEffect(owner, dispatcher) {
        val handler = BackDispatcherHandler(dispatcher)
        owner.navigationEventDispatcher.addHandler(handler)
        onDispose(handler::remove)
    }

    return dispatcher
}

private class BackDispatcherHandler(
    private val dispatcher: BackDispatcher,
) : NavigationEventHandler<NavigationEventInfo.None>(NavigationEventInfo.None, dispatcher.isEnabled) {

    init {
        dispatcher.addEnabledChangedListener { isBackEnabled = it }
    }

    override fun onBackStarted(event: NavigationEvent) {
        dispatcher.startPredictiveBack(event.toBackEvent())
    }

    override fun onBackProgressed(event: NavigationEvent) {
        dispatcher.progressPredictiveBack(event.toBackEvent())
    }

    override fun onBackCancelled() {
        dispatcher.cancelPredictiveBack()
    }

    override fun onBackCompleted() {
        dispatcher.back()
    }
}

private fun NavigationEvent.toBackEvent(): BackEvent =
    BackEvent(
        progress = progress,
        swipeEdge = when (swipeEdge) {
            NavigationEvent.EDGE_LEFT -> BackEvent.SwipeEdge.LEFT
            NavigationEvent.EDGE_RIGHT -> BackEvent.SwipeEdge.RIGHT
            else -> BackEvent.SwipeEdge.UNKNOWN
        },
        touchX = touchX,
        touchY = touchY,
    )
