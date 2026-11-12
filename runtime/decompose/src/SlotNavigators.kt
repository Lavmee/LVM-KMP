package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.router.slot.SlotNavigator
import tech.annexflow.lvm.maincontext.MainContext

/**
 * Activates a slot without a configuration of its own, such as a single dialog, and calls [onComplete] when done.
 *
 * It is a `@MainContext` function: a call from a coroutine runs on the main thread in a module that applies the
 * `tech.annexflow.lvm.main-context` Gradle plugin.
 */
@MainContext
public inline fun SlotNavigator<Unit>.activate(crossinline onComplete: () -> Unit = {}) {
    navigate(transformer = { Unit }, onComplete = { _, _ -> onComplete() })
}
