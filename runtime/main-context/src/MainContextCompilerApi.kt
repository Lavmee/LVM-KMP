package tech.annexflow.lvm.maincontext

/**
 * Marks API that the main-context compiler plugin calls. Source code doesn't use it, and it can change in any
 * release.
 */
@RequiresOptIn(
    message = "Called by the main-context compiler plugin. Annotate the function with @MainContext instead.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class MainContextCompilerApi
