package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.StackAnimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalDecomposeApi::class)
class StackAnimatorsTest {

    @Test
    fun `slide moves the child in front by at most half its width and the child behind by all of it`() =
        runComposeUiTest {
            push(slide(invert = false, animationSpec = LinearSecond))

            mainClock.advanceTimeBy(250)
            onNodeWithTag("b").assertLeftPositionInRootIsEqualTo(ChildWidth / 2)

            mainClock.advanceTimeBy(500)
            assertTrue(left("b") in 0.dp..ChildWidth / 2)
            assertTrue(left("a") < -ChildWidth / 2)
        }

    @Test
    fun `an inverted slide moves the children to the other side`() = runComposeUiTest {
        push(slide(invert = true, animationSpec = LinearSecond))

        mainClock.advanceTimeBy(750)

        assertTrue(left("b") in -ChildWidth / 2..0.dp)
        assertTrue(left("a") > ChildWidth / 2)
    }

    @Test
    fun `verticalSlide moves only the child in front`() = runComposeUiTest {
        push(verticalSlide(animationSpec = LinearSecond))

        mainClock.advanceTimeBy(500)

        onNodeWithTag("a").assertTopPositionInRootIsEqualTo(0.dp)
        assertTrue(top("b") > 0.dp && top("b") < ChildHeight)
    }

    @Test
    fun `none shows the child in front in place and unchanged while the animation runs`() = runComposeUiTest {
        push(none())

        mainClock.advanceTimeBy(150)

        onNodeWithTag("a").assertExists()
        onNodeWithTag("b").assertLeftPositionInRootIsEqualTo(0.dp).assertTopPositionInRootIsEqualTo(0.dp)
        val pixels = onNodeWithTag("b").captureToImage().toPixelMap()
        assertEquals(ChildColors.getValue("b"), pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun `slowFade keeps the child that leaves for half a second`() = runComposeUiTest {
        push(slowFade())

        mainClock.advanceTimeBy(400)
        onNodeWithTag("a").assertExists()

        mainClock.advanceTimeBy(200)
        onNodeWithTag("a").assertDoesNotExist()
    }

    /** Shows the stack of "a", pauses the clock and pushes "b" with [animator]. */
    private fun ComposeUiTest.push(animator: StackAnimator) {
        var stack by mutableStateOf(stackOf("a"))
        mainClock.autoAdvance = false
        setContent { TestChildStack(stack, stackAnimation { animator }) }
        stack = stackOf("a", "b")
    }

    private fun ComposeUiTest.left(tag: String): Dp = onNodeWithTag(tag).getUnclippedBoundsInRoot().left

    private fun ComposeUiTest.top(tag: String): Dp = onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private companion object {
        val LinearSecond = tween<Float>(durationMillis = 1000, easing = LinearEasing)
    }
}
