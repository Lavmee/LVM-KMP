package tech.annexflow.lvm.core

/** Marks API that is easy to misuse. Read its documentation, then opt in with `@OptIn(DelicateApi::class)`. */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
public annotation class DelicateApi
