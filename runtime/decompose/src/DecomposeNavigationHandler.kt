package tech.annexflow.lvm.decompose

import tech.annexflow.lvm.maincontext.MainContext

/**
 * Handles the navigation requests of type [T] that a component sends to its parent.
 *
 * [invoke] is a `@MainContext` function: in a module that applies the `tech.annexflow.lvm.main-context` Gradle plugin,
 * a call from a coroutine runs on the main thread, as Decompose's navigation requires. Implementations inherit it.
 */
public fun interface DecomposeNavigationHandler<T : Any> {
    @MainContext
    public operator fun invoke(intent: T)
}
