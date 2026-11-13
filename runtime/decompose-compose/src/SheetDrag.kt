package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Immutable

/** Where a [ChildSlotModalBottomSheet] can be dragged from. */
@Immutable
public sealed interface SheetDrag {

    /** The sheet drags from any place of it. */
    public data object Anywhere : SheetDrag

    /** The sheet doesn't drag at all: it closes from code, on a tap outside of it or on back. */
    public data object Nowhere : SheetDrag
}
