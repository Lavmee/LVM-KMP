package tech.annexflow.lvm.decompose.permissions

import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController

/**
 * A [PermissionsController] for previews and tests that answers every permission with [state] and shows nothing.
 *
 * `providePermission` returns for [PermissionState.Granted], throws `DeniedAlwaysException` for
 * [PermissionState.DeniedAlways] and throws `DeniedException` for every other state. `openAppSettings` does nothing.
 */
@Suppress("FunctionNaming") // A factory named after the type it creates, like MutableStateFlow().
public expect fun StubPermissionsController(state: PermissionState = PermissionState.Denied): PermissionsController
