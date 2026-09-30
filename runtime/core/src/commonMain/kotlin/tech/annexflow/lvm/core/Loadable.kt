@file:Suppress("NOTHING_TO_INLINE")

package tech.annexflow.lvm.core

/** A value that loads: [Loading] first, then [Loaded] with the data or [Failed] with the exception. */
public sealed interface Loadable<out T> {

    /** The value is loading. */
    public data object Loading : Loadable<Nothing>

    /** A finished load: [Loaded] or [Failed]. */
    public sealed interface Done<out T> : Loadable<T>

    /** The load failed with [throwable]. */
    public data class Failed(val throwable: Throwable) : Done<Nothing>

    /** The load finished with [data]. */
    public data class Loaded<out T>(val data: T) : Done<T>
}

/** Applies [transform] to the data of a [Loadable.Loaded] state; other states are returned as they are. */
public inline fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    Loadable.Loading -> Loadable.Loading
    is Loadable.Failed -> this
    is Loadable.Loaded<T> -> Loadable.Loaded(transform(data))
}

/** Applies [transform] to every element of a loaded list; other states are returned as they are. */
public inline fun <T, R> Loadable<List<T>>.mapList(transform: (T) -> R): Loadable<List<R>> =
    map { list -> list.map(transform) }

/** The data of a [Loadable.Loaded] state, or the result of [defaultValue] otherwise. */
public inline fun <T> Loadable<T>.getOrElse(defaultValue: () -> T): T {
    if (this is Loadable.Loaded<T>) return this.data
    return defaultValue()
}

/** The data of a [Loadable.Loaded] state, or `null` otherwise. */
public inline fun <T> Loadable<T>.getOrNull(): T? {
    if (this is Loadable.Loaded<T>) return this.data
    return null
}

/** The loaded list, or an empty list otherwise. */
public inline fun <T> Loadable<List<T>>.getOrEmpty(): List<T> = getOrElse(::emptyList)
