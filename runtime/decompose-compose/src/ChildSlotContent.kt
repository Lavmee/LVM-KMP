package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value

/** A [ChildSlotContent] for a slot that Decompose gives as a [Value]. */
@Composable
public fun <C : Any, T : Any> ChildSlotContent(
    slot: Value<ChildSlot<C, T>>,
    content: @Composable (T) -> Unit,
) {
    val currentSlot by slot.subscribeAsState()

    ChildSlotContent(slot = currentSlot, content = content)
}

/**
 * Shows the child of [slot] while the slot has one, without animation. A new child gets a composition of its own, so
 * state remembered for one child doesn't carry over to the next.
 *
 * Use it for a child that opens a window of its own, such as a `Dialog` or an `AlertDialog`: the window opens and
 * closes with the composition, and an animation drawn in the parent window doesn't reach it. A child drawn in the same
 * window that should slide in and out goes into [ChildSlotOverlay].
 */
@Composable
public fun <C : Any, T : Any> ChildSlotContent(
    slot: ChildSlot<C, T>,
    content: @Composable (T) -> Unit,
) {
    slot.child?.let { child ->
        // Decompose's key of the child comes from its configuration. Unlike the instance, it stays the same when the
        // component is created again, for example after process death, so rememberSaveable restores the content.
        key(child.key) { content(child.instance) }
    }
}
