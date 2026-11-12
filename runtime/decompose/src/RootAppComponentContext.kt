package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.doOnDestroy
import tech.annexflow.lvm.logger.Logger

/**
 * The root [AppComponentContext] of a component tree on top of [componentContext], such as a
 * `DefaultComponentContext`, with [logger] for every component of the tree.
 *
 * It creates the `InstanceKeeper` that the tree shares and destroys it when the lifecycle of [componentContext] is
 * destroyed. On JVM, build [componentContext] on a `LifecycleRegistry` that follows the app's window.
 */
public fun rootAppComponentContext(componentContext: ComponentContext, logger: Logger): AppComponentContext {
    val scopedInstanceKeeper = InstanceKeeperDispatcher()
    componentContext.lifecycle.doOnDestroy(scopedInstanceKeeper::destroy)
    return DefaultAppComponentContext(
        delegate = componentContext,
        logger = logger,
        scopedInstanceKeeper = scopedInstanceKeeper,
    )
}
