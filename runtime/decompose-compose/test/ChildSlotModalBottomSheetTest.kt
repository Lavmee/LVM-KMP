package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Child
import androidx.navigationevent.DirectNavigationEventInput
import com.arkivanov.decompose.router.slot.ChildSlot
import com.composeunstyled.ModalBottomSheetProperties
import com.composeunstyled.ModalBottomSheetState
import com.composeunstyled.SheetDetent
import com.composeunstyled.rememberModalBottomSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class ChildSlotModalBottomSheetTest {

    @Test
    fun `the sheet shows the child of the slot`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        setContent {
            ChildSlotModalBottomSheet(slot = slot, onDismiss = {}) { instance -> Content(instance) }
        }

        slot = slotOf("first")
        waitForIdle()

        onNodeWithTag("first").assertExists()
    }

    @Test
    fun `a slot closed by the app hides the sheet without onDismiss`() = runComposeUiTest {
        var slot by mutableStateOf(slotOf("first"))
        val dismissed = mutableListOf<String>()
        setContent {
            ChildSlotModalBottomSheet(slot = slot, onDismiss = { dismissed += it }) { instance -> Content(instance) }
        }
        waitForIdle()

        slot = ChildSlot()
        waitForIdle()

        onNodeWithTag("first").assertDoesNotExist()
        assertEquals(emptyList(), dismissed)
    }

    @Test
    fun `a slot open in the first frame shows the sheet and a tap outside dismisses its child`() =
        runComposeUiTest {
            val dismissed = mutableListOf<String>()
            lateinit var state: ModalBottomSheetState
            setContent {
                state = rememberModalBottomSheetState(
                    initialDetent = SheetDetent.Hidden,
                    detents = listOf(SheetDetent.Hidden, SheetDetent.FullyExpanded),
                )
                ChildSlotModalBottomSheet(
                    slot = slotOf("first"),
                    onDismiss = { dismissed += it },
                    state = state,
                    overlay = { Scrim(modifier = Modifier.testTag(ScrimTag)) },
                ) { instance -> Content(instance) }
            }
            waitForIdle()

            onNodeWithTag("first").assertExists()
            assertEquals(SheetDetent.FullyExpanded, state.currentDetent)
            assertEquals(emptyList(), dismissed)

            onNodeWithTag(ScrimTag).performTouchInput { click(topCenter) }
            waitForIdle()

            assertEquals(listOf("first"), dismissed)
        }

    @Test
    fun `the scrim fades in when the slot opens`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        mainClock.autoAdvance = false
        setContent {
            ChildSlotModalBottomSheet(
                slot = slot,
                onDismiss = {},
                overlay = {
                    Scrim(
                        modifier = Modifier.testTag(ScrimTag),
                        scrimColor = Color.Red,
                        enter = fadeIn(tween(durationMillis = 1_000)),
                        exit = fadeOut(tween(durationMillis = 1_000)),
                    )
                },
            ) { instance -> Content(instance) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(100)
        val earlyColor = scrimColor()
        mainClock.autoAdvance = true
        waitForIdle()

        assertNotEquals(Color.Red, earlyColor)
        assertEquals(Color.Red, scrimColor())
    }

    // The sheet doesn't drag in these tests: a drag catches a touch while the sheet animates, and would hide what the
    // input blocker does.
    @Test
    fun `input to the sheet waits for the end of its animation`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        var clicks = 0
        mainClock.autoAdvance = false
        setContent {
            ChildSlotModalBottomSheet(slot = slot, onDismiss = {}, drag = SheetDrag.Nowhere) { instance ->
                Content(instance, Modifier.clickable { clicks++ })
            }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(200)
        onNodeWithTag("first").performTouchInput { click(topCenter) }
        assertEquals(0, clicks)

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag("first").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `input reaches the animating sheet when it isn't blocked`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        var clicks = 0
        mainClock.autoAdvance = false
        setContent {
            ChildSlotModalBottomSheet(
                slot = slot,
                onDismiss = {},
                drag = SheetDrag.Nowhere,
                disableInputDuringAnimation = false,
            ) { instance -> Content(instance, Modifier.clickable { clicks++ }) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(200)
        onNodeWithTag("first").performTouchInput { click(topCenter) }

        assertEquals(1, clicks)
    }

    // On iOS and JVM the sheet closes on back through Compose's navigation events; on Android compose-unstyled closes
    // it.
    @Test
    fun `content takes back from the sheet while it has something to close`() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        var isEnabled by mutableStateOf(true)
        var backs = 0
        val dismissed = mutableListOf<String>()
        setContent {
            ConnectInput(input)
            ChildSlotModalBottomSheet(slot = slotOf("first"), onDismiss = { dismissed += it }) { instance ->
                BackHandler(rememberWindowBackHandler(), isEnabled) { backs++ }
                Content(instance)
            }
        }
        waitForIdle()

        runOnIdle { input.backCompleted() }
        waitForIdle()
        assertEquals(1, backs)
        assertEquals(emptyList(), dismissed)

        isEnabled = false
        waitForIdle()
        runOnIdle { input.backCompleted() }
        waitForIdle()
        assertEquals(1, backs)
        assertEquals(listOf("first"), dismissed)
    }

    @Test
    fun `back leaves the sheet open when its properties say so`() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        val dismissed = mutableListOf<String>()
        setContent {
            ConnectInput(input)
            ChildSlotModalBottomSheet(
                slot = slotOf("first"),
                onDismiss = { dismissed += it },
                properties = ModalBottomSheetProperties(dismissOnBackPress = false),
            ) { instance -> Content(instance) }
        }
        waitForIdle()

        runOnIdle { input.backCompleted() }
        waitForIdle()

        onNodeWithTag("first").assertExists()
        assertEquals(emptyList(), dismissed)
    }

    @Test
    fun `a dismissal calls the latest onDismiss`() = runComposeUiTest {
        var screen by mutableStateOf("first screen")
        val calls = mutableListOf<String>()
        setContent {
            val current = screen
            ChildSlotModalBottomSheet(
                slot = slotOf("first"),
                onDismiss = { calls += "$current: $it" },
                overlay = { Scrim(modifier = Modifier.testTag(ScrimTag)) },
            ) { instance -> Content(instance) }
        }
        waitForIdle()

        screen = "second screen"
        waitForIdle()
        onNodeWithTag(ScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()

        assertEquals(listOf("second screen: first"), calls)
    }

    @Test
    fun `windowContent is in the composition while the sheet is shown`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        var windows = 0
        setContent {
            ChildSlotModalBottomSheet(
                slot = slot,
                onDismiss = {},
                windowContent = {
                    DisposableEffect(Unit) {
                        windows++
                        onDispose { windows-- }
                    }
                },
            ) { instance -> Content(instance) }
        }
        waitForIdle()
        assertEquals(0, windows)

        slot = slotOf("first")
        waitForIdle()
        assertEquals(1, windows)

        slot = ChildSlot()
        waitForIdle()
        assertEquals(0, windows)
    }

    @Test
    fun `modifier applies to the sheet`() = runComposeUiTest {
        setContent {
            ChildSlotModalBottomSheet(
                slot = slotOf("first"),
                onDismiss = {},
                modifier = Modifier.height(300.dp).testTag(SheetTag),
            ) { instance -> Content(instance) }
        }

        onNodeWithTag(SheetTag).assertHeightIsEqualTo(300.dp)
    }

    private fun ComposeUiTest.scrimColor(): Color {
        val pixels = onNodeWithTag(ScrimTag).captureToImage().toPixelMap()
        return pixels[1, 1]
    }

    private companion object {
        const val ScrimTag = "scrim"
        const val SheetTag = "sheet"
    }
}

internal fun slotOf(instance: String): ChildSlot<String, String> =
    ChildSlot(child = Child.Created(configuration = instance, instance = instance))

/** Sheet content tagged with [instance]. */
@androidx.compose.runtime.Composable
internal fun Content(instance: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(200.dp).testTag(instance))
}
