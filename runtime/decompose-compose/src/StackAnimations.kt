package tech.annexflow.lvm.decompose.compose

import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.StackAnimator
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation as decomposeStackAnimation

/**
 * A [StackAnimation] that asks [selector] for the [StackAnimator] of each child that enters or leaves, with the
 * change described by a [SelectorContext]. A `null` animator shows the child without animation.
 *
 * It is Decompose's `stackAnimation` with a selector, which takes the same [disableInputDuringAnimation] and
 * [predictiveBackParams].
 */
@ExperimentalDecomposeApi
public fun <C : Any, T : Any> stackAnimation(
    disableInputDuringAnimation: Boolean = true,
    predictiveBackParams: (ChildStack<C, T>) -> PredictiveBackParams? = { null },
    selector: SelectorContext<C, T>.() -> StackAnimator?,
): StackAnimation<C, T> =
    decomposeStackAnimation(
        disableInputDuringAnimation = disableInputDuringAnimation,
        predictiveBackParams = predictiveBackParams,
    ) { child, otherChild, direction, isPredictiveBack ->
        SelectorContext(child, otherChild, direction, isPredictiveBack).selector()
    }
