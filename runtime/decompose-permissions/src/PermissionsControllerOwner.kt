package tech.annexflow.lvm.decompose.permissions

import dev.icerock.moko.permissions.PermissionsController
import tech.annexflow.lvm.decompose.AppComponentContext

/**
 * A component that exposes its [PermissionsController], for example to a UI that binds it with moko-permissions'
 * `BindEffect(component.permissionsController)`. A component implements it with the controller of its tree:
 *
 * ```kotlin
 * class CameraComponent(context: AppComponentContext) : AppComponentContext by context, PermissionsControllerOwner {
 *     override val permissionsController: PermissionsController
 *         get() = locals[LocalPermissionsController]
 * }
 * ```
 *
 * Inside such a component, `permissionsController` is this member, because a member wins over the extension
 * [AppComponentContext.permissionsController] of the same name. That is why the getter reads
 * [LocalPermissionsController] from `locals`: `get() = permissionsController` would call itself. Code that holds the
 * component as an [AppComponentContext] gets the extension, which reads the same local.
 */
public interface PermissionsControllerOwner {
    /** The controller that the component requests permissions with. */
    public val permissionsController: PermissionsController
}
