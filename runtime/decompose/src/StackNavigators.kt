package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.router.stack.StackNavigator
import tech.annexflow.lvm.maincontext.MainContext

/**
 * Replaces the whole stack with [configurations], the last one on top, and calls [onComplete] when done.
 *
 * Decompose has a function with the same name and parameters; this one is a `@MainContext` function, so a call from a
 * coroutine runs on the main thread in a module that applies the `tech.annexflow.lvm.main-context` Gradle plugin.
 * Import `tech.annexflow.lvm.decompose.replaceAll` for it.
 */
@MainContext
public inline fun <C : Any> StackNavigator<C>.replaceAll(
    vararg configurations: C,
    crossinline onComplete: () -> Unit = {},
) {
    navigate(transformer = { configurations.toList() }, onComplete = { _, _ -> onComplete() })
}
