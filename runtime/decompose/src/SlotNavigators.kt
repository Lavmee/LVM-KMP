package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.router.slot.SlotNavigator
import tech.annexflow.lvm.maincontext.MainContext

/**
 * Activates a slot without a configuration of its own, such as a single dialog, and calls [onComplete] when done.
 *
 * It is a `@MainContext` function: in a module that applies the `tech.annexflow.lvm.main-context` Gradle plugin, a
 * call from a coroutine runs on the main thread. The import `tech.annexflow.lvm.decompose.activate` also brings in
 * LVM's `activate(configuration)`, so a file with it doesn't import Decompose's `activate`.
 */
@MainContext
public inline fun SlotNavigator<Unit>.activate(crossinline onComplete: () -> Unit = {}) {
    navigate(transformer = { Unit }, onComplete = { _, _ -> onComplete() })
}

/**
 * Activates the child of [configuration] in the slot, dismissing the active one, and calls [onComplete] when done. A
 * child whose configuration equals [configuration] stays active.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.activate` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun <C : Any> SlotNavigator<C>.activate(configuration: C, crossinline onComplete: () -> Unit = {}) {
    navigate(transformer = { configuration }, onComplete = { _, _ -> onComplete() })
}

/**
 * Dismisses the active child of the slot, if there is one, and calls [onComplete] with `true` if there was.
 *
 * Decompose has a function with the same name and parameters. This one is a `@MainContext` function: in a module that
 * applies the `tech.annexflow.lvm.main-context` Gradle plugin, a call from a coroutine runs on the main thread. Import
 * `tech.annexflow.lvm.decompose.dismiss` instead of Decompose's: in a file that imports both, a call is ambiguous and
 * doesn't compile.
 */
@MainContext
public inline fun SlotNavigator<*>.dismiss(crossinline onComplete: (isSuccess: Boolean) -> Unit = {}) {
    navigate(transformer = { null }, onComplete = { _, oldConfiguration -> onComplete(oldConfiguration != null) })
}
