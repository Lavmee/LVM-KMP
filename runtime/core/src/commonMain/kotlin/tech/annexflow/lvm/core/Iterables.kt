package tech.annexflow.lvm.core

/** The first element of type [T], or `null` if there is none. */
public inline fun <reified T> Iterable<*>.firstOrNullIsInstance(): T? =
    firstOrNull { item -> item is T } as? T

/** The last element of type [T], or `null` if there is none. */
public inline fun <reified T> Iterable<*>.lastOrNullIsInstance(): T? =
    lastOrNull { item -> item is T } as? T

/** The first element of type [T]. Throws [IllegalArgumentException] if there is none. */
public inline fun <reified T> Iterable<*>.firstIsInstance(): T =
    requireNotNull(value = firstOrNullIsInstance())

/** The last element of type [T]. Throws [IllegalArgumentException] if there is none. */
public inline fun <reified T> Iterable<*>.lastIsInstance(): T =
    requireNotNull(value = lastOrNullIsInstance())

/** The first element matching [predicate]; if none matches, the first element; `null` if this is empty. */
public inline fun <T> Iterable<T>.findOrFirstOrNull(predicate: (T) -> Boolean): T? =
    firstOrNull(predicate) ?: firstOrNull()
