package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreateSimple
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns [scopedInstanceKeeper], an `InstanceKeeper` that every context of a component tree shares. Every
 * [AppComponentContext] is one.
 *
 * The root creates shared instances with [getOrCreateScoped], and any component of the tree reads them with
 * [getScoped]. The key is the type argument as written, so create and read with the same type: an instance created as
 * `MutableStateFlow<Data>` is not found as `StateFlow<Data>`. A [StateFlow] that components read as `StateFlow<Data>`,
 * whatever the root builds it as, goes into the tree with [getOrCreateScopedStateFlow] and is read with
 * [getScopedStateFlow].
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

/**
 * The tree's [StateFlow] of values of type [T]. If there is none, [factory] creates it.
 *
 * The root creates the flow and keeps its own type [S], for example a `MutableStateFlow` that it writes to, while the
 * components of the tree read it as `StateFlow<T>` with [getScopedStateFlow]. Both types come from the result of
 * [factory]:
 *
 * ```kotlin
 * val settings = getOrCreateScopedStateFlow { MutableStateFlow(Settings()) } // MutableStateFlow<Settings>
 * ```
 *
 * The tree keeps one flow for each [T], under the key `typeOf<StateFlow<T>>()`, so `getScoped<StateFlow<T>>()` finds it
 * too.
 * [T] is the value type of the flow that [factory] returns, and `StateFlow<Session>` and `StateFlow<Session.SignedIn>`
 * are different keys. A factory that returns `MutableStateFlow(Session.SignedIn(user))` creates a flow of
 * `Session.SignedIn`, which a reader of `StateFlow<Session>` doesn't find. Pass the value type to share it as a flow of
 * `Session`:
 *
 * ```kotlin
 * getOrCreateScopedStateFlow<Session, _> { MutableStateFlow(Session.SignedIn(user)) }
 * ```
 *
 * In the same way, `StateFlow<Item?>` and `StateFlow<Item>` are different keys.
 *
 * Like [getOrCreateScoped], this function never closes the flow.
 *
 * @throws IllegalStateException if the tree's flow of [T] is not an [S], for example a `MutableStateFlow` asked for
 * while the root created a read-only one.
 */
public inline fun <reified T, reified S : StateFlow<T>> ScopedInstanceKeeperOwner.getOrCreateScopedStateFlow(
    factory: () -> S,
): S {
    val flow = getOrCreateScoped<StateFlow<T>>(factory = factory)
    return checkNotNull(flow as? S) {
        "A ${flow::class.simpleName}, not a ${S::class.simpleName}, is shared under ${typeOf<StateFlow<T>>()}"
    }
}

/**
 * The tree's [StateFlow] of values of type [T], whatever type [getOrCreateScopedStateFlow] created it as. [T] may be
 * nullable, as in `StateFlow<Item?>`.
 *
 * @throws IllegalArgumentException if there is none: create it with [getOrCreateScopedStateFlow] in the root.
 */
public inline fun <reified T> ScopedInstanceKeeperOwner.getScopedStateFlow(): StateFlow<T> =
    requireNotNull(getScopedStateFlowOrNull<T>()) { "No shared StateFlow of ${typeOf<T>()}" }

/**
 * The tree's [StateFlow] of values of type [T], whatever type [getOrCreateScopedStateFlow] created it as, or `null` if
 * there is none.
 */
public inline fun <reified T> ScopedInstanceKeeperOwner.getScopedStateFlowOrNull(): StateFlow<T>? =
    getScopedOrNull<StateFlow<T>>()
