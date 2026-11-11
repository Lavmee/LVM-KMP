package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.ComponentContextFactory
import com.arkivanov.decompose.GenericComponentContext
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.InstanceKeeperOwner
import com.arkivanov.essenty.lifecycle.LifecycleOwner
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.statekeeper.StateKeeperOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.annexflow.lvm.logger.Logger

/**
 * An [AppComponentContext] on top of a Decompose context [delegate], such as a `DefaultComponentContext`.
 *
 * [logger] and [scopedInstanceKeeper] pass unchanged to every child. A root usually gets an `InstanceKeeperDispatcher`
 * that is destroyed together with the root's lifecycle. [initialLocals] are the locals of this context.
 *
 * [componentScope] runs on `Dispatchers.Main.immediate` with a `SupervisorJob` and is cancelled when the lifecycle is
 * destroyed. It is created on first use, so building a context needs no main dispatcher.
 */
public class DefaultAppComponentContext<D : GenericComponentContext<D>>(
    private val delegate: D,
    override val logger: Logger,
    override val scopedInstanceKeeper: InstanceKeeper,
    initialLocals: ComponentLocals = ComponentLocals.Empty,
) : AppComponentContext,
    LifecycleOwner by delegate,
    StateKeeperOwner by delegate,
    InstanceKeeperOwner by delegate,
    BackHandlerOwner by delegate {

    private var currentLocals: ComponentLocals = initialLocals
    private var hasChildren = false

    override val locals: ComponentLocals get() = currentLocals

    override val componentScope: CoroutineScope by lazy {
        coroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    }

    override fun <T> provide(local: ComponentLocal<T>, value: T): T {
        check(!hasChildren) {
            "provide($local) was called after a child of this context was created, so that child doesn't get the " +
                "value. Call provide above childStack, childSlot or childContext."
        }
        currentLocals = currentLocals.with(arrayOf(local provides value))
        return value
    }

    internal fun withLocals(values: Array<out ProvidedValue<*>>): DefaultAppComponentContext<D> =
        DefaultAppComponentContext(delegate, logger, scopedInstanceKeeper, currentLocals.with(values))

    override val componentContextFactory: ComponentContextFactory<AppComponentContext> =
        ComponentContextFactory { lifecycle, stateKeeper, instanceKeeper, backHandler ->
            hasChildren = true
            DefaultAppComponentContext(
                delegate = delegate.componentContextFactory(lifecycle, stateKeeper, instanceKeeper, backHandler),
                logger = logger,
                scopedInstanceKeeper = scopedInstanceKeeper,
                initialLocals = currentLocals,
            )
        }
}
