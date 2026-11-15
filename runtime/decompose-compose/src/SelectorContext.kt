package tech.annexflow.lvm.decompose.compose

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import kotlin.jvm.JvmName

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

/**
 * Calls [child] if the instance of [SelectorContext.child] is a [C] and the instance of [SelectorContext.otherChild]
 * an [O], and [other] if the child is an [O] and the other child a [C]. A selector picks an animator with it by the
 * types of the two screens a change is between, for either of them entering or leaving. If [C] and [O] are the same
 * type and both children are one, both are called.
 */
@JvmName("animatePair") // On JVM, both overloads take the same parameters; the one of a single type keeps the name.
public inline fun <reified C : Any, reified O : Any> SelectorContext<*, *>.animate(
    child: () -> Unit,
    other: () -> Unit,
) {
    if (this.child.instance is C && otherChild.instance is O) child()
    if (this.child.instance is O && otherChild.instance is C) other()
}
