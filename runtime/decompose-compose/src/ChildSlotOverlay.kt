package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value

/** A [ChildSlotOverlay] for a slot that Decompose gives as a [Value]. */
@Composable
public fun <C : Any, T : Any> ChildSlotOverlay(
    slot: Value<ChildSlot<C, T>>,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Child.Created<C, T>?>.() -> ContentTransform = { verticalSlide() },
    disableInputDuringAnimation: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val currentSlot by slot.subscribeAsState()

    ChildSlotOverlay(
        slot = currentSlot,
        modifier = modifier,
        transitionSpec = transitionSpec,
        disableInputDuringAnimation = disableInputDuringAnimation,
        content = content,
    )
}

/**
 * Shows the child of [slot] over what is drawn before it. When the slot gets a child, the child slides up from the
 * bottom; when the slot is empty again, the child slides down and keeps showing its own instance until it is gone. A
 * new child replaces the previous one with the same transition. A child that is in the slot from the first frame
 * shows without animation. [transitionSpec] replaces the slide: it chooses the transition from the slot's child before
 * and after the change, `null` for an empty slot.
 *
 * The overlay fills the space it gets, and the child is placed at its top start corner. [modifier] applies to the
 * overlay, which stays in place while the children slide, so it sets where the overlay is and how large it is; a
 * modifier for the child itself, such as its background, goes into [content]. The overlay takes no input of its own:
 * touches that the child doesn't consume reach what is drawn behind it. While a transition runs, the overlay blocks
 * all input, unless [disableInputDuringAnimation] is `false`.
 *
 * The animation moves only what is drawn in this window. A child that opens a window of its own, such as a `Dialog` or
 * an `AlertDialog`, goes into [ChildSlotContent] instead.
 */
@Composable
public fun <C : Any, T : Any> ChildSlotOverlay(
    slot: ChildSlot<C, T>,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Child.Created<C, T>?>.() -> ContentTransform = { verticalSlide() },
    disableInputDuringAnimation: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState = slot.child, label = "ChildSlotOverlay")
    // isRunning turns true only in the frame after the target changes, and the two states are equal while a transition
    // turns back to the state it started from: each check covers what the other misses.
    val isAnimating = transition.isRunning || transition.currentState != transition.targetState

    Box(modifier = modifier) {
        // AnimatedContent gives each content its own state, so a leaving child shows its own instance until its exit
        // ends. The content key is Decompose's key of the child, as in ChildSlotContent.
        transition.AnimatedContent(transitionSpec = transitionSpec, contentKey = { child -> child?.key }) { child ->
            // An empty slot fills the overlay too, so the overlay keeps its size and children slide by its height.
            Box(modifier = Modifier.fillMaxSize()) {
                if (child != null) content(child.instance)
            }
        }
        // Outside of AnimatedContent, so that the blocker covers the overlay instead of sliding with a child.
        if (disableInputDuringAnimation && isAnimating) {
            InputBlocker(modifier = Modifier.matchParentSize())
        }
    }
}

private fun verticalSlide(): ContentTransform =
    slideInVertically { height -> height } togetherWith slideOutVertically { height -> height }
