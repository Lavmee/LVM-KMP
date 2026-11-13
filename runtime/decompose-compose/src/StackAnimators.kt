package tech.annexflow.lvm.decompose.compose

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimator
import com.arkivanov.decompose.extensions.compose.stack.animation.isFront

private const val SLOW_FADE_DURATION_MILLIS = 500

// The factor goes from 1 to 0 for a child that enters in front and from 0 to 1 for one that leaves from the front.
// Capping it at a half moves the child in front by half its width while the child behind moves by all of it: the
// different distances in the same time make the parallax.
private const val MAX_FRONT_SLIDE_FACTOR = 0.5F

/**
 * A [StackAnimator] that keeps a child in place, unchanged, for the time of the animation, for example the screen
 * under one that enters with [verticalSlide].
 */
@ExperimentalDecomposeApi
public fun none(): StackAnimator = stackAnimator { _, _ -> Modifier }

/** Decompose's fade, slowed down to half a second. */
@ExperimentalDecomposeApi
public fun slowFade(): StackAnimator = fade(tween(SLOW_FADE_DURATION_MILLIS))

/**
 * A horizontal slide with parallax. The child in front enters from the end side and leaves to it, moving by up to
 * half its width; the child behind moves by its full width to the start side and back. [invert] swaps the sides.
 */
@ExperimentalDecomposeApi
public fun slide(invert: Boolean, animationSpec: FiniteAnimationSpec<Float> = spring()): StackAnimator =
    stackAnimator(animationSpec = animationSpec) { factor, _ ->
        Modifier.offsetXFactor(factor.coerceAtMost(MAX_FRONT_SLIDE_FACTOR) * if (invert) -1 else 1)
    }

/** A vertical slide of the child in front: it enters from the bottom and leaves to it. The child behind stays. */
@ExperimentalDecomposeApi
public fun verticalSlide(animationSpec: FiniteAnimationSpec<Float> = tween()): StackAnimator =
    stackAnimator(animationSpec = animationSpec) { factor, direction ->
        if (direction.isFront) Modifier.offsetYFactor(factor) else Modifier
    }
