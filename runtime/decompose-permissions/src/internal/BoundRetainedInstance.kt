package tech.annexflow.lvm.decompose.permissions.internal

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreateSimple

/**
 * The instance under [key], created with [create] on the first call, after [bind] was called with it. [bind] runs on
 * every call, so an instance that outlives the activity is bound to the current one.
 */
internal inline fun <T> InstanceKeeper.boundRetainedInstance(key: Any, create: () -> T, bind: (T) -> Unit): T =
    getOrCreateSimple(key = key, factory = create).also(bind)
