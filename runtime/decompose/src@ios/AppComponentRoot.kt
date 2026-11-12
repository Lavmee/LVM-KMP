package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.ApplicationLifecycle
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import tech.annexflow.lvm.logger.Logger

/**
 * The root of an iOS app's component tree on [lifecycle], with [logger] for every component. Create one for the
 * app's lifetime, for example in the app delegate, and build the root component on [context].
 *
 * [savedState] restores the state that [saveState] returned, for example from `NSCoder.decodeSavedState` during
 * UIKit state restoration, or is `null` for a fresh start.
 */
public class AppComponentRoot(logger: Logger, savedState: SerializableContainer?, lifecycle: Lifecycle) {

    /**
     * A root on Essenty's `ApplicationLifecycle`, which follows the app's foreground and background notifications for
     * as long as the app runs.
     */
    public constructor(logger: Logger, savedState: SerializableContainer?) :
        this(logger, savedState, ApplicationLifecycle())

    private val stateKeeper = StateKeeperDispatcher(savedState)

    /**
     * Receives the app's back gestures, such as the edge swipe of a navigation stack: `back()` passes one to the
     * components and returns whether one of them handled it, and `isEnabled` tells whether one would.
     */
    public val backDispatcher: BackDispatcher = BackDispatcher()

    /** The context of the root component. */
    public val context: AppComponentContext = rootAppComponentContext(
        componentContext = DefaultComponentContext(
            lifecycle = lifecycle,
            stateKeeper = stateKeeper,
            backHandler = backDispatcher,
        ),
        logger = logger,
    )

    /** The state of the whole tree, to restore as the `savedState` of a new root. */
    public fun saveState(): SerializableContainer = stateKeeper.save()
}
