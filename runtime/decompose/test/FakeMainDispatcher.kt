package tech.annexflow.lvm.decompose

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable

/** Runs every block at once, so a test sees which of the two dispatchers a scope uses. */
class FakeMainDispatcher(private val isImmediate: Boolean = false) : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = if (isImmediate) this else FakeMainDispatcher(isImmediate = true)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !isImmediate

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
