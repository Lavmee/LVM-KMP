package tech.annexflow.lvm.decompose.permissions

import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionDelegate
import dev.icerock.moko.permissions.PermissionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StubPermissionsControllerTest {

    @Test
    fun `by default the stub denies every permission`() = runTest {
        val controller = StubPermissionsController()

        assertEquals(PermissionState.Denied, controller.getPermissionState(Camera))
        assertFalse(controller.isPermissionGranted(Camera))
        val failure = assertFailsWith<DeniedException> { controller.providePermission(Camera) }
        assertFalse(failure is DeniedAlwaysException)
    }

    @Test
    fun `a granting stub grants every permission`() = runTest {
        val controller = StubPermissionsController(PermissionState.Granted)

        controller.providePermission(Camera)

        assertTrue(controller.isPermissionGranted(Camera))
    }

    @Test
    fun `a stub that denies always fails requests as denied always`() = runTest {
        val controller = StubPermissionsController(PermissionState.DeniedAlways)

        assertFailsWith<DeniedAlwaysException> { controller.providePermission(Camera) }
    }
}

/** A permission the stub never asks the system about. */
private object Camera : Permission {
    override val delegate: PermissionDelegate get() = error("The stub doesn't use the delegate")
}
