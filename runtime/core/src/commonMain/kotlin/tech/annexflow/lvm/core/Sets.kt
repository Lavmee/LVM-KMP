@file:Suppress("NOTHING_TO_INLINE")

package tech.annexflow.lvm.core

/** This set without [element] if it contains it, and with [element] otherwise. */
public inline fun <T> Set<T>.switchElement(element: T): Set<T> =
    if (this.contains(element)) this.minusElement(element) else this.plusElement(element)
