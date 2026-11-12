package tech.annexflow.lvm.decompose

import androidx.activity.ComponentActivity
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.retainedComponent
import tech.annexflow.lvm.logger.Logger

/**
 * The root component of this activity, created by [factory] on a root [AppComponentContext] and retained across
 * configuration changes, as Decompose's `retainedComponent` does: [factory] runs once, and the component lives until
 * the activity finishes. The tree's shared instances live as long as the component.
 *
 * [key] must be unique within the activity. [handleBackButton] passes the activity's back button to the tree.
 * [discardSavedState] ignores the saved state, for example to apply a deep link from `onCreate`;
 * [isStateSavingAllowed] is asked before the state is saved. Don't let the component hold the activity.
 */
// Decompose's retainedComponent takes five parameters; the logger of the root context is the sixth.
@Suppress("LongParameterList")
public fun <T> ComponentActivity.retainedAppComponent(
    logger: Logger,
    key: String = "RootRetainedComponent",
    handleBackButton: Boolean = true,
    discardSavedState: Boolean = false,
    isStateSavingAllowed: () -> Boolean = { true },
    factory: (AppComponentContext) -> T,
): T = retainedComponent(
    key = key,
    handleBackButton = handleBackButton,
    discardSavedState = discardSavedState,
    isStateSavingAllowed = isStateSavingAllowed,
) { componentContext -> factory(rootAppComponentContext(componentContext, logger)) }

/**
 * A root [AppComponentContext] attached to this activity: its lifecycle, saved state, retained instances and back
 * button, as Decompose's `defaultComponentContext` does. Call it once in `onCreate`; the tree is created again with
 * the activity. [discardSavedState] and [isStateSavingAllowed] work as in [retainedAppComponent].
 */
public fun ComponentActivity.defaultAppComponent(
    logger: Logger,
    discardSavedState: Boolean = false,
    isStateSavingAllowed: () -> Boolean = { true },
): AppComponentContext = rootAppComponentContext(
    componentContext = defaultComponentContext(
        discardSavedState = discardSavedState,
        isStateSavingAllowed = isStateSavingAllowed,
    ),
    logger = logger,
)
