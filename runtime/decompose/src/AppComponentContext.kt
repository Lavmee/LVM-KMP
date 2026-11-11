package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.GenericComponentContext
import tech.annexflow.lvm.logger.LoggerHolder

/**
 * The component context of an app: Decompose's lifecycle, state keeper, instance keeper and back handler, plus
 * [ComponentLocals], a [componentScope] tied to the lifecycle, instances shared by the whole tree and a logger.
 *
 * Child contexts that Decompose creates from it, such as those of `childStack` or `childContext`, are
 * [AppComponentContext]s with the same logger and shared instances, and with the locals this context had when the
 * child was created. [DefaultAppComponentContext] implements it, and a component usually delegates to the context it
 * receives: `class Profile(context: AppComponentContext) : AppComponentContext by context`.
 */
public interface AppComponentContext :
    GenericComponentContext<AppComponentContext>,
    ComponentLocalsOwner,
    CoroutineScopeOwner,
    ScopedInstanceKeeperOwner,
    LoggerHolder {

    /**
     * Gives [local] the value [value] in this context and in the children created after this call, and returns
     * [value]. Call it before the first child is created, for example in a property above `childStack`: children
     * created earlier keep the values they had.
     *
     * It is a member, not an extension, so it works through `by` delegation.
     *
     * @throws IllegalStateException if a child of this context was already created.
     */
    public fun <T> provide(local: ComponentLocal<T>, value: T): T

    /** Lets apps declare extensions such as `AppComponentContext.preview()`. */
    public companion object
}

/**
 * A copy of this context with [values] added to its locals, for this subtree only. The receiver keeps its own values
 * and stays open to [AppComponentContext.provide]. Pass the copy to a child component, or create children from it.
 */
public fun AppComponentContext.provideLocals(vararg values: ProvidedValue<*>): AppComponentContext = when {
    values.isEmpty() -> this
    this is DefaultAppComponentContext<*> -> withLocals(values)
    else -> DefaultAppComponentContext(
        delegate = this,
        logger = logger,
        scopedInstanceKeeper = scopedInstanceKeeper,
        initialLocals = locals.with(values),
    )
}
