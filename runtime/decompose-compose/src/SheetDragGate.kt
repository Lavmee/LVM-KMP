package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity

/**
 * What the handles of a sheet tell its gate. Both values live within a gesture or between the attach and detach of
 * handle nodes, so they are plain fields, not snapshot state.
 */
internal class SheetDragGate {

    /** Set by a handle on the down of a gesture that started on it, and cleared by the gate when the gesture ends. */
    var gestureStartedOnHandle: Boolean = false

    /** How many handles are attached in the sheet's content, enabled or not. */
    var handleCount: Int = 0
}

internal val LocalSheetDragGate = staticCompositionLocalOf<SheetDragGate?> { null }

/**
 * The gate that decides whether a gesture reaches the sheet's drag in the [SheetDrag.Handle] and
 * [SheetDrag.HandleOnly] modes. It wraps the sheet's content, inside the sheet's drag.
 */
internal fun Modifier.sheetDragGate(gate: SheetDragGate, drag: SheetDrag): Modifier =
    when (drag) {
        SheetDrag.Anywhere, SheetDrag.Nowhere -> this
        SheetDrag.Handle -> this then SheetDragGateElement(gate, drag)
        SheetDrag.HandleOnly -> this then SheetDragGateElement(gate, drag) then Modifier.nestedScroll(BlockHandOff)
    }

private data class SheetDragGateElement(
    private val gate: SheetDragGate,
    private val drag: SheetDrag,
) : ModifierNodeElement<SheetDragGateNode>() {

    override fun create(): SheetDragGateNode = SheetDragGateNode(gate, drag)

    override fun update(node: SheetDragGateNode) {
        node.gate = gate
        node.drag = drag
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sheetDragGate"
    }
}

private class SheetDragGateNode(
    var gate: SheetDragGate,
    var drag: SheetDrag,
) : DelegatingNode() {

    init {
        delegate(SuspendingPointerInputModifierNode { gateGestures() })
    }

    // The gate claims a gesture the way Compose's own detectors do. Consuming every move at once would break the
    // content: scrollable and clickable wait for the touch slop and give up when someone else consumed the move, and
    // the gate, outside of them, would consume it before a list could claim a scroll or a click could happen. So the
    // gate waits for the slop too: if the content claimed the gesture first, the wait returns null and the gate stays
    // out of it; if nobody claimed it, the gate consumes it at the slop, still before the sheet's drag, which is
    // outside the gate.
    private suspend fun PointerInputScope.gateGestures() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            check(gate.handleCount > 0) {
                "SheetDrag.$drag needs a Modifier.sheetDragHandle() in the content of the sheet"
            }

            try {
                if (gate.gestureStartedOnHandle) return@awaitEachGesture

                val claimed = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                if (claimed != null) verticalDrag(claimed.id) { change -> change.consume() }
            } finally {
                gate.gestureStartedOnHandle = false
            }
        }
    }
}

// Takes everything that nested scrolling hands to the sheet. onPostScroll and onPostFling go from the inside out, so
// this connection comes before the sheet's.
private val BlockHandOff = object : NestedScrollConnection {

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(x = 0F, y = available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = 0F, y = available.y)
}
