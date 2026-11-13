package tech.annexflow.lvm.decompose.compose

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction

/**
 * A change of the stack as a [stackAnimation] selector sees it: [child] enters or leaves in [direction], and
 * [otherChild] takes its place or gives it up. [isPredictiveBack] is `true` while a predictive back gesture drives
 * the change.
 */
public data class SelectorContext<C : Any, T : Any>(
    public val child: Child.Created<C, T>,
    public val otherChild: Child.Created<C, T>,
    public val direction: Direction,
    public val isPredictiveBack: Boolean,
)

/**
 * Calls [child] if the instance of [SelectorContext.child] is a [C], and [other] if the instance of
 * [SelectorContext.otherChild] is a [C]. A selector picks an animator with it by the type of a screen, the same for
 * the screen itself and for the screen under it.
 */
public inline fun <reified C : Any> SelectorContext<*, *>.animate(child: () -> Unit, other: () -> Unit) {
    if (this.child.instance is C) child()
    if (otherChild.instance is C) other()
}
