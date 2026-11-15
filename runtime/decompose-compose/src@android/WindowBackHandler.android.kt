// The JVM class named after the common file, which compiled callers link against.
@file:JvmName("WindowBackHandlerKt")

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
import kotlin.jvm.JvmName

// The same code as WindowBackHandler.nonAndroid.kt, compiled against Google's navigationevent-compose at the version
// androidx.activity depends on. Common code can't use navigationevent: its dependency would raise that version.
@Composable
public actual fun rememberWindowBackHandler(): BackHandler {
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
