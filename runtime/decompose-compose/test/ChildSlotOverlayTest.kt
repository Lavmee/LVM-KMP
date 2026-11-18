package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChildSlotOverlayTest {

    @Test
    fun `the overlay shows the child while the slot has one`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        setContent {
            ChildSlotOverlay(slot = slot) { instance -> Content(instance) }
        }
        onNodeWithTag("first").assertDoesNotExist()

        slot = slotOf("first")
        waitForIdle()
        onNodeWithTag("first").assertIsDisplayed()

        slot = ChildSlot()
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
    }

    @Test
    fun `the overlay follows a slot given as a Value`() = runComposeUiTest {
        val slot = MutableValue(ChildSlot<String, String>())
        setContent {
            ChildSlotOverlay(slot = slot) { instance -> Content(instance) }
        }
        onNodeWithTag("first").assertDoesNotExist()

        slot.value = slotOf("first")
        waitForIdle()
        onNodeWithTag("first").assertIsDisplayed()

        slot.value = ChildSlot()
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
    }

    @Test
    fun `by default the child slides up from the bottom and back down`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(slot = slot) { instance -> Content(instance) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(100)
        assertTrue(top("first") > 0.dp)

        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(0.dp, top("first"))

        mainClock.autoAdvance = false
        slot = ChildSlot()
        mainClock.advanceTimeBy(100)
        assertTrue(top("first") > 0.dp)
    }

    @Test
    fun `a child in the slot from the first frame shows without animation`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(slot = slotOf("first"), transitionSpec = { linearSlide() }) { instance ->
                Content(instance)
            }
        }
        mainClock.advanceTimeByFrame()

        assertEquals(0.dp, top("first"))
    }

    @Test
    fun `the leaving child is drawn with its own instance until its exit ends`() = runComposeUiTest {
        var slot by mutableStateOf(slotOf("first"))
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(slot = slot, transitionSpec = { linearSlide() }) { instance -> Content(instance) }
        }
        mainClock.advanceTimeByFrame()

        slot = ChildSlot()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("first").assertIsDisplayed()

        mainClock.advanceTimeBy(HalfwayMillis)
        onNodeWithTag("first").assertIsDisplayed()

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
    }

    @Test
    fun `a new child replaces the previous one`() = runComposeUiTest {
        var slot by mutableStateOf(slotOf("first"))
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(slot = slot, transitionSpec = { linearSlide() }) { instance -> Content(instance) }
        }
        mainClock.advanceTimeByFrame()

        slot = slotOf("second")
        mainClock.advanceTimeBy(HalfwayMillis)
        onNodeWithTag("first").assertIsDisplayed()
        onNodeWithTag("second").assertIsDisplayed()

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
        assertEquals(0.dp, top("second"))
    }

    @Test
    fun `input to the child waits for the end of the animation`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        var clicks = 0
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(slot = slot, transitionSpec = { linearSlide() }) { instance ->
                Content(instance, Modifier.clickable { clicks++ })
            }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(HalfwayMillis)
        onNodeWithTag("first").performTouchInput { click(topCenter) }
        assertEquals(0, clicks)

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag("first").performClick()
        assertEquals(1, clicks)
    }

    // The child doesn't consume touches, so they reach the content behind the overlay unless the blocker takes them. A
    // blocker shown only once the transition runs would miss the first frame, and a blocker that slides with the
    // children would leave the top of the screen open in the middle of the exit.
    @Test
    fun `input behind the overlay waits for the end of the exit`() = runComposeUiTest {
        var slot by mutableStateOf(slotOf("first"))
        var clicks = 0
        mainClock.autoAdvance = false
        setContent {
            Box {
                Box(modifier = Modifier.fillMaxSize().clickable { clicks++ }.testTag(BehindTag))
                ChildSlotOverlay(slot = slot, transitionSpec = { linearSlide() }) { instance -> Content(instance) }
            }
        }
        mainClock.advanceTimeByFrame()

        slot = ChildSlot()
        mainClock.advanceTimeByFrame()
        onNodeWithTag(BehindTag).performTouchInput { click(topCenter) }
        mainClock.advanceTimeBy(HalfwayMillis)
        onNodeWithTag(BehindTag).performTouchInput { click(topCenter) }
        assertEquals(0, clicks)

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(BehindTag).performTouchInput { click(topCenter) }
        assertEquals(1, clicks)
    }

    @Test
    fun `input reaches the animating child when it isn't blocked`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        var clicks = 0
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(
                slot = slot,
                transitionSpec = { linearSlide() },
                disableInputDuringAnimation = false,
            ) { instance -> Content(instance, Modifier.clickable { clicks++ }) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(HalfwayMillis)
        onNodeWithTag("first").performTouchInput { click(topCenter) }

        assertEquals(1, clicks)
    }

    @Test
    fun `modifier applies to the overlay that stays in place while the child slides`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(
                slot = slot,
                modifier = Modifier.testTag(OverlayTag),
                transitionSpec = { linearSlide() },
            ) { instance -> Content(instance) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(HalfwayMillis)

        assertTrue(top("first") > 0.dp)
        assertEquals(0.dp, top(OverlayTag))
    }

    @Test
    fun `transitionSpec replaces the slide`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        mainClock.autoAdvance = false
        setContent {
            ChildSlotOverlay(
                slot = slot,
                transitionSpec = { fadeIn(tween(DurationMillis)) togetherWith fadeOut(tween(DurationMillis)) },
            ) { instance -> Content(instance) }
        }

        slot = slotOf("first")
        mainClock.advanceTimeBy(HalfwayMillis)

        onNodeWithTag("first").assertExists()
        assertEquals(0.dp, top("first"))
    }

    @Test
    fun `a child created again gets its saved state back`() = runComposeUiTest {
        var instance = "first"
        val recreationTester = RecreationTester(this)
        recreationTester.setContent {
            ChildSlotOverlay(slot = slotOf(configuration = "config", instance = instance)) { current ->
                val savedInstance = rememberSaveable { current }
                Content(savedInstance)
            }
        }

        // The component is created again for the same configuration, as after process death.
        instance = "second"
        recreationTester.recreate()

        onNodeWithTag("first").assertExists()
    }

    private fun ComposeUiTest.top(tag: String): Dp = onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun linearSlide(): ContentTransform =
        slideInVertically(tween(DurationMillis, easing = LinearEasing)) { height -> height } togetherWith
            slideOutVertically(tween(DurationMillis, easing = LinearEasing)) { height -> height }

    private companion object {
        const val DurationMillis = 1_000
        const val HalfwayMillis = DurationMillis / 2L
        const val BehindTag = "behind"
        const val OverlayTag = "overlay"
    }
}
