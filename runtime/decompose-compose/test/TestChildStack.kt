package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.StackAnimation
import com.arkivanov.decompose.router.stack.ChildStack

internal val ChildWidth = 100.dp
internal val ChildHeight = 60.dp
internal val ChildColors = mapOf("a" to Color.Red, "b" to Color.Blue)

/**
 * Shows each child of [stack] as a box of [ChildWidth] by [ChildHeight] in its color of [ChildColors], tagged with its
 * configuration.
 */
@OptIn(ExperimentalDecomposeApi::class)
@Composable
internal fun TestChildStack(stack: ChildStack<String, String>, animation: StackAnimation<String, String>) {
    ChildStack(stack = stack, animation = animation) { child ->
        Box(
            Modifier
                .size(ChildWidth, ChildHeight)
                .background(ChildColors.getValue(child.configuration))
                .testTag(child.configuration),
        )
    }
}

internal fun stackOf(vararg configurations: String): ChildStack<String, String> =
    ChildStack(active = created(configurations.last()), backStack = configurations.dropLast(1).map(::created))

internal fun created(configuration: String): Child.Created<String, String> =
    Child.Created(configuration = configuration, instance = configuration)
