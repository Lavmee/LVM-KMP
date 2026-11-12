package tech.annexflow.lvm.decompose.permissions

import androidx.activity.ComponentActivity
import com.arkivanov.essenty.instancekeeper.instanceKeeper
import dev.icerock.moko.permissions.PermissionsController
import tech.annexflow.lvm.decompose.permissions.internal.boundRetainedInstance

private const val PERMISSIONS_CONTROLLER_KEY = "LvmPermissionsController"

/**
 * The [PermissionsController] of this activity, kept across activity recreation and bound to the current activity.
 *
 * Call it in every `onCreate`, outside the factory of a retained component, which runs only once. The first call
 * creates the controller and keeps it in the activity's `InstanceKeeper`; every call binds it to this activity. A
 * retained controller that is bound only once points at a destroyed activity after recreation: its requests fail, and
 * the answer to a permission dialog that was open during recreation never arrives. Binding again is safe, and the
 * activity hands the pending answer to the newly bound controller.
 */
public fun ComponentActivity.retainedPermissionsController(): PermissionsController =
    instanceKeeper().boundRetainedInstance(
        key = PERMISSIONS_CONTROLLER_KEY,
        create = { PermissionsController(applicationContext) },
        bind = { controller -> controller.bind(this) },
    )
