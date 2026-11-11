package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeper
import tech.annexflow.lvm.logger.Logger

/**
 * A root [AppComponentContext] for previews and tests, which runs without a UI.
 *
 * By default it is resumed, keeps no saved state and logs nothing. A test passes a `LifecycleRegistry` to move the
 * lifecycle and destroy the component, a `StateKeeperDispatcher` to save and restore state, and a [logger] to see what
 * the component logs. Shared instances live in the context's own `InstanceKeeper`, which is destroyed with the
 * lifecycle. [componentScope] needs a main dispatcher; a JVM test sets one with `Dispatchers.setMain` from
 * kotlinx-coroutines-test.
 */
public class PreviewAppComponentContext private constructor(
    context: DefaultAppComponentContext<ComponentContext>,
) : AppComponentContext by context {

    public constructor(
        lifecycle: Lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED),
        stateKeeper: StateKeeper? = null,
        logger: Logger = Logger { _, _, _, _ -> },
    ) : this(DefaultComponentContext(lifecycle = lifecycle, stateKeeper = stateKeeper), logger)

    private constructor(context: ComponentContext, logger: Logger) : this(
        DefaultAppComponentContext(
            delegate = context,
            logger = logger,
            scopedInstanceKeeper = context.instanceKeeper,
        ),
    )
}
