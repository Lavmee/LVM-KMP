package tech.annexflow.lvm.maincontext

/**
 * Marks a function that must run on the main thread.
 *
 * In code compiled with the `tech.annexflow.lvm.main-context` Gradle plugin, a call to the function from code that can
 * suspend moves to `Dispatchers.Main.immediate`, together with its receivers and arguments, and the caller suspends
 * until it returns. A call made on the main thread runs at once. The arguments are computed on the main thread too, so
 * compute expensive ones before the call.
 *
 * Code can suspend inside a suspend function or a suspend lambda, and inside a lambda passed to an inline function
 * there, unless the parameter is `crossinline` or `noinline`. Calls from other code, calls through function references
 * and calls in default parameter values run where they are made.
 *
 * Overrides of an annotated function inherit the annotation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class MainContext
