package tech.annexflow.lvm.decompose.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Moves the content down by [factor] times its height, or up for a negative factor. The content keeps its place in
 * the layout.
 */
public fun Modifier.offsetYFactor(factor: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(x = 0, y = (placeable.height * factor).toInt())
        }
    }

/** Moves the content to the end side by [factor] times its width, or to the start side for a negative factor. */
internal fun Modifier.offsetXFactor(factor: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(x = (placeable.width * factor).toInt(), y = 0)
        }
    }
