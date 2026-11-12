package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import tech.annexflow.lvm.logger.Logger
import tech.annexflow.lvm.logger.Severity

class RootAppComponentContextTest {

    @Test
    fun `the root's shared instances reach the whole tree and are destroyed with the root`() {
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val root = rootAppComponentContext(DefaultComponentContext(lifecycle), Logger { _, _, _, _ -> })
        val shared = root.scopedInstanceKeeper.getOrCreate(key = "shared") { RecordingInstance() }
        val grandchild = root.childContext("child").childContext("grandchild")
        assertSame(shared, grandchild.scopedInstanceKeeper.get("shared"))
        assertFalse(shared.isDestroyed)

        lifecycle.destroy()

        assertTrue(shared.isDestroyed)
    }

    @Test
    fun `every component of the tree logs through the root's logger`() {
        val logged = mutableListOf<String>()
        val logger = Logger { _, _, message, _ -> logged += message }
        val root = rootAppComponentContext(DefaultComponentContext(LifecycleRegistry()), logger)

        root.childContext("child").logger.log(Severity.INFO, "tag", "from a child", null)

        assertTrue("from a child" in logged)
    }
}

private class RecordingInstance : InstanceKeeper.Instance {
    var isDestroyed = false
        private set

    override fun onDestroy() {
        isDestroyed = true
    }
}
