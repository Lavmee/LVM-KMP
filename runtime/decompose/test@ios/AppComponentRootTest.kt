package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.childContext
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.encodeObject
import tech.annexflow.lvm.logger.Logger

// NSKeyedUnarchiver's initializer takes an error pointer, which is experimental interop API.
@OptIn(ExperimentalForeignApi::class)
class AppComponentRootTest {

    private val logger = Logger { _, _, _, _ -> }

    /** A test process has no UIApplication, which ApplicationLifecycle reads, so the roots run on a registry. */
    private fun resumed() = LifecycleRegistry(Lifecycle.State.RESUMED)

    /** UIKit state restoration hands the app a secure keyed coder; the state goes through one and back. */
    @Test
    fun `saved state survives a round trip through a secure keyed archive`() {
        val first = AppComponentRoot(logger, savedState = null, lifecycle = resumed())
        first.context.stateKeeper.register(key = "query", strategy = String.serializer()) { "saved" }
        val archiver = NSKeyedArchiver(requiringSecureCoding = true)
        archiver.encodeSavedState(first.saveState())
        archiver.finishEncoding()

        val unarchiver = NSKeyedUnarchiver(forReadingFromData = archiver.encodedData, error = null)
        val restored = assertNotNull(unarchiver.decodeSavedState())
        val next = AppComponentRoot(logger, savedState = restored, lifecycle = resumed())

        assertEquals("saved", next.context.stateKeeper.consume(key = "query", strategy = String.serializer()))
    }

    @Test
    fun `a coder without saved state gives none`() {
        val archiver = NSKeyedArchiver(requiringSecureCoding = true)
        archiver.finishEncoding()

        val unarchiver = NSKeyedUnarchiver(forReadingFromData = archiver.encodedData, error = null)

        assertNull(unarchiver.decodeSavedState())
    }

    /** An archive from an older version of the app may hold state this version can't read; the app starts fresh. */
    @Test
    fun `saved state that can't be read gives none`() {
        val archiver = NSKeyedArchiver(requiringSecureCoding = true)
        archiver.encodeObject("not saved state", forKey = SAVED_STATE_KEY)
        archiver.finishEncoding()

        val unarchiver = NSKeyedUnarchiver(forReadingFromData = archiver.encodedData, error = null)

        assertNull(unarchiver.decodeSavedState())
    }

    @Test
    fun `a back gesture reaches the component that handles it`() {
        val root = AppComponentRoot(logger, savedState = null, lifecycle = resumed())
        var handled = false
        assertFalse(root.backDispatcher.back())

        root.context.childContext("child").backHandler.register(BackCallback { handled = true })

        assertTrue(root.backDispatcher.isEnabled)
        assertTrue(root.backDispatcher.back())
        assertTrue(handled)
    }

    @Test
    fun `the root's lifecycle reaches its children`() {
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val child = AppComponentRoot(logger, savedState = null, lifecycle = lifecycle).context.childContext("child")

        lifecycle.destroy()

        assertEquals(Lifecycle.State.DESTROYED, child.lifecycle.state)
    }
}
