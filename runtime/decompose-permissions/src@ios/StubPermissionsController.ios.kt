package tech.annexflow.lvm.decompose.permissions

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import tech.annexflow.lvm.decompose.permissions.internal.provide

@Suppress("FunctionNaming") // A factory named after the type it creates, like MutableStateFlow().
public actual fun StubPermissionsController(state: PermissionState): PermissionsController = StubController(state)

private class StubController(private val state: PermissionState) : PermissionsController {

    override suspend fun providePermission(permission: Permission) {
        state.provide(permission)
    }

    override suspend fun isPermissionGranted(permission: Permission): Boolean = state == PermissionState.Granted

    override suspend fun getPermissionState(permission: Permission): PermissionState = state

    override fun openAppSettings() = Unit
}
