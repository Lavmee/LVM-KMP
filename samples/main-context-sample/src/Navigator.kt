package tech.annexflow.lvm.samples.maincontext

import tech.annexflow.lvm.maincontext.MainContext

/** Navigation that, like most UI state, may change only on the main thread. [onPush] sees every pushed screen. */
class Navigator(private val onPush: (String) -> Unit) {
    @MainContext
    fun push(screen: String) {
        onPush(screen)
    }
}

/** Loads a screen wherever the caller runs, then pushes it on the main thread. */
suspend fun Navigator.pushLoaded(load: suspend () -> String) {
    // Arguments are computed on the main thread too, so the load runs before the call.
    val screen = load()
    push(screen)
}

/** Opens screens. Implementations run on the main thread when called from a coroutine. */
interface Router {
    @MainContext
    fun open(screen: String)
}
