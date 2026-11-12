package tech.annexflow.lvm.decompose

/** Handles the actions of type [T] that a screen sends to its component, such as a click. */
public fun interface DecomposeActionHandler<T : Any> {
    public operator fun invoke(intent: T)
}
