package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Immutable

/**
 * Where a [ChildSlotModalBottomSheet] can be dragged from.
 *
 * compose-unstyled puts the drag on the whole sheet and doesn't let it move to a part of it, so the modes other than
 * [Anywhere] and [Nowhere] work through a gate that decides which gestures reach the sheet.
 */
@Immutable
public sealed interface SheetDrag {

    /** The sheet drags from any place of it. */
    public data object Anywhere : SheetDrag

    /**
     * A drag starts only from a part marked with [sheetDragHandle]. A list scrolled to its top still hands a further
     * drag down to the sheet.
     */
    public data object Handle : SheetDrag

    /**
     * A drag starts only from a part marked with [sheetDragHandle], and nested scrolling never hands a drag to the
     * sheet. A sheet already dragged down still moves back up with a scroll: that part of nested scrolling goes from
     * the outside in, before the gate can see it.
     */
    public data object HandleOnly : SheetDrag

    /** The sheet doesn't drag at all: it closes from code, on a tap outside of it or on back. */
    public data object Nowhere : SheetDrag
}
