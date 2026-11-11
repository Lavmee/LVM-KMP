package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import tech.annexflow.lvm.logger.Logger

/** A root context as an app builds one, on a lifecycle the test drives. */
fun rootContext(
    lifecycle: Lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED),
): DefaultAppComponentContext<ComponentContext> = DefaultAppComponentContext(
    delegate = DefaultComponentContext(lifecycle),
    logger = Logger { _, _, _, _ -> },
    scopedInstanceKeeper = InstanceKeeperDispatcher(),
)
