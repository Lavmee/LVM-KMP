package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import com.arkivanov.essenty.backhandler.BackHandler

/**
 * A [BackHandler] that receives the back gestures of the window this composition is in: the start, progress and
 * cancellation of a predictive back gesture, and back itself.
 *
 * A component's back handler gets the gestures through the root of its component tree. A dialog or a bottom sheet
 * takes the gestures of its window first and closes on back, so the components shown in it never see them.
 * Callbacks registered here from the content of a dialog, for example with [BackHandler], come before the dialog: back
 * reaches them while one of them is enabled, and the dialog when none is.
 *
 * It is connected to Compose's navigation event dispatcher, which on Android is the one of the window's
 * `OnBackPressedDispatcher`, such as a `ComponentDialog`'s.
 *
 * @throws IllegalStateException if the composition has no `NavigationEventDispatcherOwner`.
 */
@Composable
public expect fun rememberWindowBackHandler(): BackHandler
