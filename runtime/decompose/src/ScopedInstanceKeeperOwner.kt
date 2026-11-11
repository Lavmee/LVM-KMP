package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeper

/**
 * Owns [scopedInstanceKeeper], an `InstanceKeeper` that every context of a component tree shares. Every
 * [AppComponentContext] is one.
 */
public interface ScopedInstanceKeeperOwner {
    public val scopedInstanceKeeper: InstanceKeeper
}
