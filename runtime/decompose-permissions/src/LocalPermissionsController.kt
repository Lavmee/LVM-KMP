package tech.annexflow.lvm.decompose.permissions

import dev.icerock.moko.permissions.PermissionsController
import tech.annexflow.lvm.decompose.AppComponentContext
import tech.annexflow.lvm.decompose.ComponentLocal
import tech.annexflow.lvm.decompose.componentLocalOf

/**
 * The [PermissionsController] of a component tree. The root provides it, for example with
 * `provideLocals(LocalPermissionsController provides controller)`, and components read it with [permissionsController].
 */
public val LocalPermissionsController: ComponentLocal<PermissionsController> =
    componentLocalOf(name = "PermissionsController")

/**
 * The [PermissionsController] that the root provided under [LocalPermissionsController].
 *
 * @throws IllegalStateException if the root provided none.
 */
public val AppComponentContext.permissionsController: PermissionsController
    get() = locals[LocalPermissionsController]
