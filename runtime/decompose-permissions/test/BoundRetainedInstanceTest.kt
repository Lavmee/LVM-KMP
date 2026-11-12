package tech.annexflow.lvm.decompose.permissions

import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import tech.annexflow.lvm.decompose.permissions.internal.boundRetainedInstance

/** The rule behind retainedPermissionsController, checked without an activity. */
class BoundRetainedInstanceTest {

    @Test
    fun `the instance is created once and bound on every call`() {
        val keeper = InstanceKeeperDispatcher()
        var created = 0
        val bound = mutableListOf<String>()

        val create = { "controller ${++created}" }

        val first = keeper.boundRetainedInstance(key = "controller", create = create, bind = bound::add)
        // Activity recreation: the same keeper, a new call.
        val second = keeper.boundRetainedInstance(key = "controller", create = create, bind = bound::add)

        assertEquals(1, created)
        assertSame(first, second)
        assertEquals(listOf("controller 1", "controller 1"), bound)
    }
}
