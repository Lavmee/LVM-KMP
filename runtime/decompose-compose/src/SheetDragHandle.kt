package tech.annexflow.lvm.decompose.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * Marks a part of a [ChildSlotModalBottomSheet]'s content as a handle that the sheet drags from in the
 * [SheetDrag.Handle] and [SheetDrag.HandleOnly] modes. It works at any depth of the content and does nothing outside
 * of a sheet or in the other modes, so a screen can mark its top bar without knowing whether it is shown in a sheet.
 *
 * [enabled] `false` stops drags from this handle for a while, for example during a submit. The handle still counts as
 * present: a handle mode without any handle fails on the first touch.
 */
public fun Modifier.sheetDragHandle(enabled: Boolean = true): Modifier = this then SheetDragHandleElement(enabled)

private data class SheetDragHandleElement(
    private val enabled: Boolean,
) : ModifierNodeElement<SheetDragHandleNode>() {

    override fun create(): SheetDragHandleNode = SheetDragHandleNode(enabled)

    override fun update(node: SheetDragHandleNode) {
        node.enabled = enabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sheetDragHandle"
        properties["enabled"] = enabled
    }
}

private class SheetDragHandleNode(var enabled: Boolean) :
    Modifier.Node(),
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {

    // A reused node goes through detach and attach, so the count needs no onReset.
    override fun onAttach() {
        currentValueOf(LocalSheetDragGate)?.let { gate -> gate.handleCount += 1 }
    }

    override fun onDetach() {
        currentValueOf(LocalSheetDragGate)?.let { gate -> gate.handleCount -= 1 }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main || !enabled) return

        if (pointerEvent.changes.any { it.changedToDownIgnoreConsumed() }) {
            currentValueOf(LocalSheetDragGate)?.gestureStartedOnHandle = true
        }
    }

    override fun onCancelPointerInput() = Unit

    // The handle only watches downs and consumes nothing. Without sharing, it would take the hit test from the
    // siblings under it, and a click on a part it covers, such as a top bar, would never arrive.
    override fun sharePointerInputWithSiblings(): Boolean = true
}
