@file:Suppress("TooManyFunctions") // One for each stack navigation function of Decompose, plus pushNewWithPop.

package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.StackNavigator
import tech.annexflow.lvm.maincontext.MainContext

/**
 * Pushes [configuration] on top of the stack and calls [onComplete] when done.
 *
 * Decompose fails if the stack already holds [configuration], unless `DecomposeSettings.duplicateConfigurationsEnabled`
 * is `true`. [pushNew] does nothing when [configuration] is already on top, as after a double click.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.push` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@DelicateDecomposeApi
@MainContext
public inline fun <C : Any> StackNavigator<C>.push(configuration: C, crossinline onComplete: () -> Unit = {}) {
    navigate(transformer = { it + configuration }, onComplete = { _, _ -> onComplete() })
}

/**
 * Pushes [configuration] on top of the stack unless it is already on top, as after a double click, and calls
 * [onComplete] with `true` if it was pushed.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.pushNew` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.pushNew(
    configuration: C,
    crossinline onComplete: (isSuccess: Boolean) -> Unit = {},
) {
    navigate(
        transformer = { stack -> if (stack.last() == configuration) stack else stack + configuration },
        onComplete = { newStack, oldStack -> onComplete(newStack.size > oldStack.size) },
    )
}

/**
 * Replaces the top [n] configurations of the stack with [configuration] unless it is already on top, and calls
 * [onComplete] with `true` if the stack changed. It replaces a screen with another without stacking the same screen
 * twice, as after a double click.
 *
 * With [n] `0` it pushes [configuration], as [pushNew] does. With [n] at least the size of the stack, [configuration]
 * is the only one left. [n] must not be negative.
 *
 * It is a `@MainContext` function: in a module that applies the `tech.annexflow.lvm.main-context` Gradle plugin, a
 * call from a coroutine runs on the main thread.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.pushNewWithPop(
    configuration: C,
    n: Int = 1,
    crossinline onComplete: (isSuccess: Boolean) -> Unit = {},
) {
    require(n >= 0) { "n must not be negative, but was $n" }
    navigate(
        transformer = { stack -> if (stack.last() == configuration) stack else stack.dropLast(n) + configuration },
        onComplete = { newStack, oldStack -> onComplete(newStack != oldStack) },
    )
}

/**
 * Moves [configuration] to the top of the stack, or pushes it if the stack doesn't hold it, and calls [onComplete]
 * when done. Of several configurations equal to [configuration], it moves the one nearest the top.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.pushToFront` instead of Decompose's: in a file that imports both, a call is ambiguous
 * and doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.pushToFront(configuration: C, crossinline onComplete: () -> Unit = {}) {
    navigate(
        transformer = { stack ->
            stack.toMutableList().apply {
                val existingIndex = lastIndexOf(configuration)
                if (existingIndex >= 0) removeAt(existingIndex)
                add(configuration)
            }
        },
        onComplete = { _, _ -> onComplete() },
    )
}

/**
 * Removes every configuration of the class of [configuration] from the stack, pushes [configuration], and calls
 * [onComplete] when done. Unlike [pushToFront], it compares configurations by class, not by equality.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.bringToFront` instead of Decompose's: in a file that imports both, a call is ambiguous
 * and doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.bringToFront(configuration: C, crossinline onComplete: () -> Unit = {}) {
    navigate(
        transformer = { stack -> stack.filterNot { it::class == configuration::class } + configuration },
        onComplete = { _, _ -> onComplete() },
    )
}

/**
 * Removes the top configuration of the stack unless it is the only one, and calls [onComplete] with `true` if it was
 * removed.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.pop` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.pop(crossinline onComplete: (isSuccess: Boolean) -> Unit = {}) {
    navigate(
        transformer = { stack -> if (stack.size > 1) stack.dropLast(1) else stack },
        onComplete = { newStack, oldStack -> onComplete(newStack.size < oldStack.size) },
    )
}

/**
 * Removes configurations from the top of the stack while [predicate] returns `true` for them. The first configuration
 * stays even if [predicate] returns `true` for every one.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.popWhile` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.popWhile(crossinline predicate: (C) -> Boolean) {
    popWhile(predicate = predicate, onComplete = {})
}

/**
 * Removes configurations from the top of the stack while [predicate] returns `true` for them, and calls [onComplete]
 * with `true` if any was removed. The first configuration stays even if [predicate] returns `true` for every one.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.popWhile` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.popWhile(
    crossinline predicate: (C) -> Boolean,
    crossinline onComplete: (isSuccess: Boolean) -> Unit,
) {
    navigate(
        transformer = { stack -> stack.dropLastWhile(predicate).ifEmpty { stack.take(1) } },
        onComplete = { newStack, oldStack -> onComplete(newStack.size < oldStack.size) },
    )
}

/**
 * Removes configurations from the top of the stack until the one at [index] is on top, and calls [onComplete] with
 * `true` if any was removed. [index] must not be negative; an [index] at or past the top leaves the stack as it is.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.popTo` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.popTo(
    index: Int,
    crossinline onComplete: (isSuccess: Boolean) -> Unit = {},
) {
    require(index >= 0) { "Index must not be negative, but was $index" }
    navigate(
        transformer = { it.take(index + 1) },
        onComplete = { newStack, oldStack -> onComplete(newStack.size < oldStack.size) },
    )
}

/**
 * Removes every configuration but the first from the stack, and calls [onComplete] with `true` if any was removed.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.popToFirst` instead of Decompose's: in a file that imports both, a call is ambiguous
 * and doesn't compile.
 */
@MainContext
public inline fun StackNavigator<*>.popToFirst(crossinline onComplete: (isSuccess: Boolean) -> Unit = {}) {
    popTo(index = 0, onComplete = onComplete)
}

/**
 * Replaces the top configuration of the stack with [configuration] and calls [onComplete] when done.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.replaceCurrent` instead of Decompose's: in a file that imports both, a call is
 * ambiguous and doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.replaceCurrent(
    configuration: C,
    crossinline onComplete: () -> Unit = {},
) {
    navigate(transformer = { it.dropLast(1) + configuration }, onComplete = { _, _ -> onComplete() })
}

/**
 * Replaces the whole stack with [configurations], the last one on top, and calls [onComplete] when done.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.replaceAll` instead of Decompose's: in a file that imports both, a call is ambiguous
 * and doesn't compile.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.replaceAll(
    vararg configurations: C,
    crossinline onComplete: () -> Unit = {},
) {
    navigate(transformer = { configurations.toList() }, onComplete = { _, _ -> onComplete() })
}

/**
 * Replaces the stack with the one that [transformer] returns for it, the last configuration on top. [transformer] must
 * not return an empty list.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.navigate` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile. The import also brings in LVM's `navigate` for `SlotNavigator`. The member
 * `StackNavigator.navigate(transformer, onComplete)` is not a `@MainContext` function.
 */
@MainContext
public fun <C : Any> StackNavigator<C>.navigate(transformer: (stack: List<C>) -> List<C>) {
    navigate(transformer = transformer, onComplete = { _, _ -> })
}
