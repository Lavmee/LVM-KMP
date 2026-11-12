package tech.annexflow.lvm.decompose.permissions.internal

import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState

/** Answers a request for [permission] as a controller whose permissions are all in this state. */
internal fun PermissionState.provide(permission: Permission) {
    when (this) {
        PermissionState.Granted -> Unit
        PermissionState.DeniedAlways -> throw DeniedAlwaysException(permission)
        else -> throw DeniedException(permission)
    }
}
