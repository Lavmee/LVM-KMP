package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreateSimple
import kotlin.reflect.typeOf

/**
 * Owns [scopedInstanceKeeper], an `InstanceKeeper` that every context of a component tree shares. Every
 * [AppComponentContext] is one.
 *
 * The root creates shared instances with [getOrCreateScoped], and any component of the tree reads them with
 * [getScoped]. The key is the type argument as written, so create and read with the same type: an instance created as
 * `MutableStateFlow<Data>` is not found as `StateFlow<Data>`.
 *
 * These functions keep the instance as it is and never close it. An instance that must be closed when the tree is
 * destroyed implements `InstanceKeeper.Instance` and goes into [scopedInstanceKeeper] with `getOrCreate`.
 */
public interface ScopedInstanceKeeperOwner {
    public val scopedInstanceKeeper: InstanceKeeper
}

/** The shared instance of type [T] under [key], or `null` if there is none. */
public inline fun <reified T : Any> ScopedInstanceKeeperOwner.getScopedOrNull(key: Any = typeOf<T>()): T? =
    (scopedInstanceKeeper.get(key) as? InstanceKeeper.SimpleInstance<*>)?.instance as? T

/**
 * The shared instance of type [T] under [key].
 *
 * @throws IllegalArgumentException if there is none.
 */
public inline fun <reified T : Any> ScopedInstanceKeeperOwner.getScoped(key: Any = typeOf<T>()): T =
    requireNotNull(getScopedOrNull<T>(key)) { "No shared instance of ${typeOf<T>()} under $key" }

/** The shared instance of type [T] under [key]. If there is none, [factory] creates it. */
public inline fun <reified T : Any> ScopedInstanceKeeperOwner.getOrCreateScoped(
    key: Any = typeOf<T>(),
    factory: () -> T,
): T = scopedInstanceKeeper.getOrCreateSimple(key = key, factory = factory)
