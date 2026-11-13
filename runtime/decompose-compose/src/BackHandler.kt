package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackHandler

/**
 * Registers a back callback with [backHandler], such as a component's, while this composable is in the
 * composition. The callback is enabled while [isEnabled] is `true` and calls the latest [onBack].
 */
@Composable
public fun BackHandler(backHandler: BackHandler, isEnabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember { BackCallback(isEnabled = isEnabled) { currentOnBack() } }

    SideEffect { callback.isEnabled = isEnabled }

    DisposableEffect(backHandler) {
        backHandler.register(callback)
        onDispose { backHandler.unregister(callback) }
    }
}
