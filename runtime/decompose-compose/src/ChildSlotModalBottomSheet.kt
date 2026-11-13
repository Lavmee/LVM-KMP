package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.composeunstyled.LocalModalState
import com.composeunstyled.ModalBottomSheetOverlayScope
import com.composeunstyled.ModalBottomSheetProperties
import com.composeunstyled.ModalBottomSheetScope
import com.composeunstyled.ModalBottomSheetState
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.modalFragment
import com.composeunstyled.rememberModalBottomSheetState
import kotlinx.coroutines.launch

private val DefaultScrimColor = Color.Black.copy(alpha = 0.32F)

/** The scope of the content of a [ChildSlotModalBottomSheet]. */
public class DecomposeModalBottomSheetScope internal constructor(
    internal val bottomSheetScope: ModalBottomSheetScope,
)

/** The scope of the overlay of a [ChildSlotModalBottomSheet], such as its [Scrim]. */
public class DecomposeModalBottomSheetOverlayScope internal constructor(
    internal val bottomSheetOverlayScope: ModalBottomSheetOverlayScope,
)

/** A [ChildSlotModalBottomSheet] for a slot that Decompose gives as a [Value]. */
// The parameters are the sheet's options, each with a default, as in Compose's own components.
@Suppress("LongParameterList")
@Composable
public fun <C : Any, T : Any> ChildSlotModalBottomSheet(
    slot: Value<ChildSlot<C, T>>,
    onDismiss: (T) -> Unit,
    modifier: Modifier = Modifier,
    visibleDetent: SheetDetent = SheetDetent.FullyExpanded,
    state: ModalBottomSheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
        detents = listOf(SheetDetent.Hidden, visibleDetent),
    ),
    drag: SheetDrag = SheetDrag.Anywhere,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    disableInputDuringAnimation: Boolean = true,
    windowContent: @Composable () -> Unit = {},
    overlay: @Composable DecomposeModalBottomSheetOverlayScope.() -> Unit = { Scrim() },
    content: @Composable DecomposeModalBottomSheetScope.(T) -> Unit,
) {
    val currentSlot by slot.subscribeAsState()

    ChildSlotModalBottomSheet(
        slot = currentSlot,
        onDismiss = onDismiss,
        modifier = modifier,
        visibleDetent = visibleDetent,
        state = state,
        drag = drag,
        properties = properties,
        disableInputDuringAnimation = disableInputDuringAnimation,
        windowContent = windowContent,
        overlay = overlay,
        content = content,
    )
}

/**
 * Shows the child of [slot] in compose-unstyled's modal bottom sheet. When the slot gets a child, the sheet animates
 * to [visibleDetent]; when the slot is empty again, the sheet animates to hidden and shows the last child until it is
 * gone. When the user hides the sheet, by dragging it down, tapping outside of it or pressing back, [onDismiss]
 * receives the child: dismiss the slot there.
 *
 * [state] must list [visibleDetent] among its detents, as the default state does: the sheet animates only to detents
 * it knows. [modifier] applies to the sheet itself, outside of its drag handling, and [drag] tells where the sheet can
 * be dragged from. Input to the sheet is blocked while it animates, unless [disableInputDuringAnimation] is `false`.
 * [windowContent] runs inside the sheet's window, for example to register the window with the app or to set the
 * appearance of its system bars. [overlay] draws under the sheet, a [Scrim] by default.
 *
 * Back closes the sheet while [properties] allow it. Content that handles back itself, such as inner navigation,
 * registers with [rememberWindowBackHandler]: back reaches it first while it handles back. On Android the sheet is a
 * window of its own, and its content sees the `LocalConfiguration` of the window that shows the sheet, so a language
 * changed in place reaches an open sheet.
 */
// The parameters are the sheet's options, each with a default, as in Compose's own components.
@Suppress("LongParameterList")
@Composable
public fun <C : Any, T : Any> ChildSlotModalBottomSheet(
    slot: ChildSlot<C, T>,
    onDismiss: (T) -> Unit,
    modifier: Modifier = Modifier,
    visibleDetent: SheetDetent = SheetDetent.FullyExpanded,
    state: ModalBottomSheetState = rememberModalBottomSheetState(
        initialDetent = SheetDetent.Hidden,
        detents = listOf(SheetDetent.Hidden, visibleDetent),
    ),
    drag: SheetDrag = SheetDrag.Anywhere,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    disableInputDuringAnimation: Boolean = true,
    windowContent: @Composable () -> Unit = {},
    overlay: @Composable DecomposeModalBottomSheetOverlayScope.() -> Unit = { Scrim() },
    content: @Composable DecomposeModalBottomSheetScope.(T) -> Unit,
) {
    var shownInstance: T? by remember { mutableStateOf(slot.child?.instance) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val hostLocal = hostWindowLocal()
    val coroutineScope = rememberCoroutineScope()

    // This effect comes before the collector below, and animateTo leaves Hidden before it first suspends. So a slot
    // that is open in the first frame is not hidden when the collector first looks, and doesn't count as dismissed.
    LaunchedEffect(slot) {
        val child = slot.child
        if (child == null) {
            state.animateTo(SheetDetent.Hidden)
            shownInstance = null
        } else {
            shownInstance = child.instance
            state.animateTo(visibleDetent)
        }
    }

    // A slot closed by the app clears shownInstance right after the sheet is hidden, before the collector sees it,
    // so only the user's dismissal reaches onDismiss.
    LaunchedEffect(state) {
        snapshotFlow { state.currentDetent == SheetDetent.Hidden }
            .collect { isHidden -> if (isHidden) shownInstance?.let { currentOnDismiss(it) } }
    }

    UnstyledModalBottomSheet(
        state = state,
        enabled = drag != SheetDrag.Nowhere,
        properties = properties,
        overlay = { DecomposeModalBottomSheetOverlayScope(this).overlay() },
    ) {
        val scope = remember(this) { DecomposeModalBottomSheetScope(this) }
        val window: @Composable () -> Unit = {
            // The state's confirmDetentChange can refuse to hide, as for compose-unstyled's own dismissal.
            SheetBackHandler(enabled = properties.dismissOnBackPress) {
                coroutineScope.launch { state.animateTo(SheetDetent.Hidden) }
            }
            windowContent()
            Box {
                Sheet(modifier = modifier) {
                    shownInstance?.let { instance -> scope.content(instance) }
                }
                if (disableInputDuringAnimation && !state.isIdle) {
                    InputBlocker(modifier = Modifier.matchParentSize())
                }
            }
        }

        if (hostLocal == null) window() else CompositionLocalProvider(hostLocal, window)
    }
}

/**
 * A scrim of [scrimColor] under the sheet. It shows with [enter] when the sheet opens, also when the sheet is shown
 * from its first frame, and leaves with [exit] when the sheet closes.
 */
@Composable
public fun DecomposeModalBottomSheetOverlayScope.Scrim(
    modifier: Modifier = Modifier,
    scrimColor: Color = DefaultScrimColor,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
) {
    val modalState = LocalModalState.current
    // Starts invisible, unlike compose-unstyled's scrim: the modal is composed when it becomes visible, and a scrim
    // that started visible would skip its enter transition.
    val visibleState = remember { MutableTransitionState(initialState = false) }

    LaunchedEffect(modalState.transitionState.targetState) {
        visibleState.targetState = modalState.transitionState.targetState
    }

    AnimatedVisibility(visibleState = visibleState, enter = enter, exit = exit) {
        Box(modifier = modifier.modalFragment().fillMaxSize().background(scrimColor))
    }
}

@Composable
private fun InputBlocker(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        },
    )
}
