package tech.annexflow.lvm.core

/** A function that calls this function and applies [transform] to its result, on every call. */
public inline fun <T, R> (() -> T).map(crossinline transform: (T) -> R): () -> R =
    { transform(this()) }
