package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.Dialog
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackEvent
import kotlin.test.Test
import kotlin.test.assertEquals

// Back comes from a DirectNavigationEventInput, as the platform's own input would send it: the tests measure which
// handler gets back, not how the platform delivers it.
@OptIn(ExperimentalTestApi::class)
class WindowBackHandlerTest {

    @Test
    fun `content takes back from a dialog while it has something to close`() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        var isEnabled by mutableStateOf(true)
        var backCount = 0
        var dismissCount = 0
        setContent {
            ConnectInput(input)
            Dialog(onDismissRequest = { dismissCount++ }) {
                BackHandler(rememberWindowBackHandler(), isEnabled) { backCount++ }
            }
        }

        runOnIdle { input.backCompleted() }
        assertEquals(1, backCount)
        assertEquals(0, dismissCount)

        isEnabled = false
        waitForIdle()
        runOnIdle { input.backCompleted() }
        assertEquals(1, backCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun `the window back handler receives the whole predictive back gesture`() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        val events = mutableListOf<Any>()
        val callback = object : BackCallback() {
            override fun onBackStarted(backEvent: BackEvent) {
                events += "started"
                events += backEvent
            }

            override fun onBackProgressed(backEvent: BackEvent) {
                events += "progressed"
                events += backEvent
            }

            override fun onBackCancelled() {
                events += "cancelled"
            }

            override fun onBack() {
                events += "back"
            }
        }
        setContent {
            ConnectInput(input)
            val backHandler = rememberWindowBackHandler()
            DisposableEffect(backHandler) {
                backHandler.register(callback)
                onDispose { backHandler.unregister(callback) }
            }
        }

        runOnIdle {
            input.backStarted(
                NavigationEvent(touchX = 1F, touchY = 2F, progress = 0.1F, swipeEdge = NavigationEvent.EDGE_LEFT),
            )
            input.backProgressed(
                NavigationEvent(touchX = 3F, touchY = 4F, progress = 0.5F, swipeEdge = NavigationEvent.EDGE_LEFT),
            )
            input.backCancelled()
            input.backStarted(
                NavigationEvent(touchX = 5F, touchY = 6F, progress = 0.2F, swipeEdge = NavigationEvent.EDGE_RIGHT),
            )
            input.backCompleted()
        }

        assertEquals(
            listOf(
                "started",
                BackEvent(progress = 0.1F, swipeEdge = BackEvent.SwipeEdge.LEFT, touchX = 1F, touchY = 2F),
                "progressed",
                BackEvent(progress = 0.5F, swipeEdge = BackEvent.SwipeEdge.LEFT, touchX = 3F, touchY = 4F),
                "cancelled",
                "started",
                BackEvent(progress = 0.2F, swipeEdge = BackEvent.SwipeEdge.RIGHT, touchX = 5F, touchY = 6F),
                "back",
            ),
            events,
        )
    }

    @Test
    fun `back skips the window back handler after it leaves the composition`() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        var isShown by mutableStateOf(true)
        var backCount = 0
        var dismissCount = 0
        val callback = BackCallback { backCount++ }
        setContent {
            ConnectInput(input)
            Dialog(onDismissRequest = { dismissCount++ }) {
                if (isShown) {
                    val backHandler = rememberWindowBackHandler()
                    // Never unregistered: only the window back handler leaving the composition takes it out of the way.
                    DisposableEffect(backHandler) {
                        backHandler.register(callback)
                        onDispose {}
                    }
                }
            }
        }

        isShown = false
        waitForIdle()
        runOnIdle { input.backCompleted() }

        assertEquals(0, backCount)
        assertEquals(1, dismissCount)
    }
}
