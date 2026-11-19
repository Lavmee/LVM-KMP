package tech.annexflow.lvm.decompose.permissions

import androidx.activity.ComponentActivity
import dev.icerock.moko.permissions.PermissionsController
import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.decompose.defaultAppComponent
import tech.annexflow.lvm.decompose.provideLocals
import tech.annexflow.lvm.decompose.provides
import tech.annexflow.lvm.decompose.retainedAppComponent
import tech.annexflow.lvm.logger.Logger

/**
 * The root component of this activity, as [retainedAppComponent] creates it, on a root context that provides the
 * activity's [PermissionsController] under [LocalPermissionsController]. Components of the tree read it as
 * `permissionsController`.
 *
 * Call it in every `onCreate`. Every call takes the controller from [retainedPermissionsController], which binds it to
 * the current activity, while [factory] runs only on the first call. The tree keeps one controller, and after
 * recreation the controller requests permissions through the new activity and gets the answer to a dialog that was
 * open during recreation. The other parameters work as in [retainedAppComponent].
 */
// The parameters of retainedAppComponent, which takes six.
@Suppress("LongParameterList")
public fun <T> ComponentActivity.retainedAppComponentWithPermissions(
    logger: Logger,
    key: String = "RootRetainedComponent",
    handleBackButton: Boolean = true,
    discardSavedState: Boolean = false,
    isStateSavingAllowed: () -> Boolean = { true },
    factory: (AppComponentContext) -> T,
): T {
    // Outside the factory, which runs once: the retained controller must be bound to every new activity.
    val permissionsController = retainedPermissionsController()
    return retainedAppComponent(
        logger = logger,
        key = key,
        handleBackButton = handleBackButton,
        discardSavedState = discardSavedState,
        isStateSavingAllowed = isStateSavingAllowed,
    ) { context -> factory(context.provideLocals(LocalPermissionsController provides permissionsController)) }
}

/**
 * A root [AppComponentContext] attached to this activity, as [defaultAppComponent] creates it, that provides the
 * activity's [PermissionsController] under [LocalPermissionsController]. Call it once in `onCreate`. The controller
 * comes from [retainedPermissionsController], so every tree of this activity gets the same controller, bound to the
 * current activity. [discardSavedState] and [isStateSavingAllowed] work as in [defaultAppComponent].
 */
public fun ComponentActivity.defaultAppComponentWithPermissions(
    logger: Logger,
    discardSavedState: Boolean = false,
    isStateSavingAllowed: () -> Boolean = { true },
): AppComponentContext = defaultAppComponent(
    logger = logger,
    discardSavedState = discardSavedState,
    isStateSavingAllowed = isStateSavingAllowed,
).provideLocals(LocalPermissionsController provides retainedPermissionsController())
