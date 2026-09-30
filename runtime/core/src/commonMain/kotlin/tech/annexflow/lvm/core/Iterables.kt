package tech.annexflow.lvm.core

/** The first element of type [T], or `null` if there is none. If [T] is nullable, a `null` element matches. */
public inline fun <reified T> Iterable<*>.firstOrNullIsInstance(): T? {
    for (element in this) if (element is T) return element
    return null
}

/** The last element of type [T], or `null` if there is none. If [T] is nullable, a `null` element matches. */
public inline fun <reified T> Iterable<*>.lastOrNullIsInstance(): T? {
    var last: T? = null
    var found = false
    for (element in this) {
        if (element is T) {
            last = element
            found = true
        }
    }
    return if (found) last else null
}

/**
 * The first element of type [T]. Throws [NoSuchElementException] if there is none.
 * If [T] is nullable, a `null` element matches.
 */
public inline fun <reified T> Iterable<*>.firstIsInstance(): T {
    for (element in this) if (element is T) return element
    throw NoSuchElementException("No element of type ${T::class.simpleName}")
}

/**
 * The last element of type [T]. Throws [NoSuchElementException] if there is none.
 * If [T] is nullable, a `null` element matches.
 */
public inline fun <reified T> Iterable<*>.lastIsInstance(): T {
    var last: T? = null
    var found = false
    for (element in this) {
        if (element is T) {
            last = element
            found = true
        }
    }
    if (!found) throw NoSuchElementException("No element of type ${T::class.simpleName}")
    return last as T
}

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
