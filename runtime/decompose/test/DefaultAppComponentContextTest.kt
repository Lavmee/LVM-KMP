package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppComponentContextTest {

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `componentScope runs on the immediate main dispatcher`() {
        val main = FakeMainDispatcher()
        Dispatchers.setMain(main)

        val scope = rootContext().componentScope

        assertSame(main.immediate, scope.coroutineContext[ContinuationInterceptor])
    }

    @Test
    fun `componentScope is cancelled when the lifecycle is destroyed`() {
        Dispatchers.setMain(FakeMainDispatcher())
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = rootContext(lifecycle)
        val scope = context.componentScope

        lifecycle.destroy()

        assertFalse(scope.isActive)
    }

    @Test
    fun `componentScope of a destroyed context is cancelled`() {
        Dispatchers.setMain(FakeMainDispatcher())
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = rootContext(lifecycle)
        lifecycle.destroy()

        assertFalse(context.componentScope.isActive)
    }

    @Test
    fun `a failed job leaves componentScope active`() {
        Dispatchers.setMain(FakeMainDispatcher())
        val scope = rootContext().componentScope

        scope.launch(CoroutineExceptionHandler { _, _ -> }) { error("failed") }

        assertTrue(scope.isActive)
    }

    /** JVM has no main dispatcher without Swing or JavaFX, and the Android host has none without Robolectric. */
    @Test
    fun `a context is built and read without a main dispatcher`() {
        val local = componentLocalOf<String>()
        val root = rootContext()

        root.provide(local, "value")
        val copy = root.provideLocals(local provides "copy")

        assertEquals("value", root.locals[local])
        assertEquals("copy", copy.locals[local])
    }
}
