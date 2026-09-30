package tech.annexflow.lvm.core

/** The first element of type [T], or `null` if there is none. */
public inline fun <reified T> Iterable<*>.firstOrNullIsInstance(): T? =
    firstOrNull { item -> item is T } as? T

/** The last element of type [T], or `null` if there is none. */
public inline fun <reified T> Iterable<*>.lastOrNullIsInstance(): T? =
    lastOrNull { item -> item is T } as? T

/** The first element of type [T]. Throws [NoSuchElementException] if there is none. */
public inline fun <reified T> Iterable<*>.firstIsInstance(): T =
    firstOrNullIsInstance<T>() ?: throw NoSuchElementException("No element of type ${T::class.simpleName}")

/** The last element of type [T]. Throws [NoSuchElementException] if there is none. */
public inline fun <reified T> Iterable<*>.lastIsInstance(): T =
    lastOrNullIsInstance<T>() ?: throw NoSuchElementException("No element of type ${T::class.simpleName}")

/** The first element matching [predicate]; if none matches, the first element; `null` if this is empty. */
public inline fun <T> Iterable<T>.findOrFirstOrNull(predicate: (T) -> Boolean): T? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    val first = iterator.next()
    var element = first
    var matches = predicate(element)
    while (!matches && iterator.hasNext()) {
        element = iterator.next()
        matches = predicate(element)
    }
    return if (matches) element else first
}
