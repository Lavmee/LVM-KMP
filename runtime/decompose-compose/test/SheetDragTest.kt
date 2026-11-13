package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composeunstyled.ModalBottomSheetProperties
import com.composeunstyled.ModalBottomSheetState
import com.composeunstyled.SheetDetent
import com.composeunstyled.rememberModalBottomSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// A real finger moves a pixel or two before it passes the touch slop. Until then, neither a list nor the sheet has
// claimed the gesture, and a node that consumed the move then would cancel both: the gestures below start that way.
@OptIn(ExperimentalTestApi::class)
class SheetDragTest {

    @Test
    fun `anywhere drags the sheet from its body without a handle`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Anywhere) { Body() }

        onNodeWithTag(BodyTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.Hidden, state.currentDetent)
    }

    @Test
    fun `handle mode drags the sheet from the handle and not from the body`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Handle) { HandleAndBody() }

        // A gesture on the handle first: the next gesture, from the body, must not count as one from the handle.
        onNodeWithTag(HandleTag).performClick()
        onNodeWithTag(BodyTag).performTouchInput { dragDown() }
        waitForIdle()
        assertEquals(SheetDetent.FullyExpanded, state.currentDetent)

        onNodeWithTag(HandleTag).performTouchInput { dragDown() }
        waitForIdle()
        assertEquals(SheetDetent.Hidden, state.currentDetent)
    }

    @Test
    fun `handle mode hands a drag down from a list at its top to the sheet`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Handle) { HandleAndList() }

        onNodeWithTag(ListTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.Hidden, state.currentDetent)
    }

    @Test
    fun `handle only mode keeps a drag down from a list at its top in the list`() = runComposeUiTest {
        val state = openSheet(SheetDrag.HandleOnly) { HandleAndList() }

        onNodeWithTag(ListTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.FullyExpanded, state.currentDetent)
        onNodeWithText("item 0").assertIsDisplayed()
    }

    @Test
    fun `handle only mode drags the sheet from the handle`() = runComposeUiTest {
        val state = openSheet(SheetDrag.HandleOnly) { HandleAndList() }

        onNodeWithTag(HandleTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.Hidden, state.currentDetent)
    }

    @Test
    fun `a list scrolls in handle mode`() = runComposeUiTest {
        openSheet(SheetDrag.Handle) { HandleAndList() }

        onNodeWithTag(ListTag).performTouchInput { dragUp() }
        waitForIdle()

        onNodeWithText("item 0").assertDoesNotExist()
    }

    @Test
    fun `a list scrolls in handle only mode`() = runComposeUiTest {
        openSheet(SheetDrag.HandleOnly) { HandleAndList() }

        onNodeWithTag(ListTag).performTouchInput { dragUp() }
        waitForIdle()

        onNodeWithText("item 0").assertDoesNotExist()
    }

    @Test
    fun `nowhere mode drags the sheet neither from the handle nor from a list`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Nowhere) { HandleAndList() }

        onNodeWithTag(HandleTag).performTouchInput { dragDown() }
        waitForIdle()
        onNodeWithTag(ListTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.FullyExpanded, state.currentDetent)
    }

    @Test
    fun `nowhere mode needs no handle`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Nowhere) { Body() }

        onNodeWithTag(BodyTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.FullyExpanded, state.currentDetent)
    }

    @Test
    fun `handle mode without a handle fails on the first touch and names the modifier`() = runComposeUiTest {
        openSheet(SheetDrag.Handle) { Body() }

        val failure = assertFailsWith<Throwable> {
            onNodeWithTag(BodyTag).performTouchInput { dragDown() }
            waitForIdle()
        }

        assertTrue(generateSequence(failure) { it.cause }.any { "sheetDragHandle" in it.message.orEmpty() })
    }

    @Test
    fun `content stays clickable in handle mode`() = runComposeUiTest {
        var clicks = 0
        openSheet(SheetDrag.Handle) { HandleAndBody(Modifier.clickable { clicks++ }) }

        onNodeWithTag(BodyTag).performTouchInput { tapWithJitter() }
        waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled handle doesn't drag the sheet and still counts as a handle`() = runComposeUiTest {
        val state = openSheet(SheetDrag.Handle) { Handle(enabled = false) }

        onNodeWithTag(HandleTag).performTouchInput { dragDown() }
        waitForIdle()

        assertEquals(SheetDetent.FullyExpanded, state.currentDetent)
    }

    @Test
    fun `a handle outside of a sheet lets the sibling under it take clicks`() = runComposeUiTest {
        var clicks = 0
        setContent {
            Box(modifier = Modifier.size(64.dp)) {
                Box(modifier = Modifier.matchParentSize().testTag(BodyTag).clickable { clicks++ })
                Box(modifier = Modifier.matchParentSize().sheetDragHandle())
            }
        }

        onNodeWithTag(BodyTag).performClick()

        assertEquals(1, clicks)
    }

    private fun ComposeUiTest.openSheet(
        drag: SheetDrag,
        content: @Composable () -> Unit,
    ): ModalBottomSheetState {
        lateinit var state: ModalBottomSheetState
        setContent {
            state = rememberModalBottomSheetState(initialDetent = SheetDetent.FullyExpanded)
            ChildSlotModalBottomSheet(
                slot = slotOf("first"),
                onDismiss = {},
                state = state,
                drag = drag,
                properties = ModalBottomSheetProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) { content() }
        }
        waitForIdle()
        return state
    }

    private companion object {
        const val HandleTag = "handle"
        const val BodyTag = "body"
        const val ListTag = "list"
        const val DragStepPx = 200F
        const val SubSlopStepPx = 2F
        val ListItems = List(size = 50) { index -> "item $index" }

        fun TouchInjectionScope.dragDown() {
            down(center)
            moveBy(Offset(x = 0F, y = SubSlopStepPx))
            moveBy(Offset(x = 0F, y = DragStepPx))
            moveBy(Offset(x = 0F, y = DragStepPx))
            up()
        }

        fun TouchInjectionScope.dragUp() {
            down(center)
            moveBy(Offset(x = 0F, y = -SubSlopStepPx))
            moveBy(Offset(x = 0F, y = -DragStepPx))
            moveBy(Offset(x = 0F, y = -DragStepPx))
            up()
        }

        // A tap moves a pixel or two, and a click is cancelled when someone consumed that move.
        fun TouchInjectionScope.tapWithJitter() {
            down(center)
            moveBy(Offset(x = 0F, y = SubSlopStepPx))
            up()
        }

        @Composable
        fun Body(modifier: Modifier = Modifier) {
            Box(modifier = modifier.fillMaxWidth().height(240.dp).testTag(BodyTag))
        }

        @Composable
        fun Handle(enabled: Boolean = true) {
            Box(modifier = Modifier.fillMaxWidth().height(48.dp).testTag(HandleTag).sheetDragHandle(enabled))
        }

        @Composable
        fun HandleAndBody(bodyModifier: Modifier = Modifier) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Handle()
                Body(bodyModifier)
            }
        }

        @Composable
        fun HandleAndList() {
            Column(modifier = Modifier.fillMaxWidth()) {
                Handle()
                LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp).testTag(ListTag)) {
                    items(ListItems) { item -> BasicText(text = item) }
                }
            }
        }
    }
}
