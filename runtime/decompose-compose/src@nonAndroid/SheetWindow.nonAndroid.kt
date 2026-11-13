package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

@Composable
internal actual fun hostWindowLocal(): ProvidedValue<*>? = null

// compose-unstyled closes the sheet on the Escape key only, and on iOS not at all.
@Composable
internal actual fun SheetBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(backHandler = rememberWindowBackHandler(), isEnabled = enabled, onBack = onBack)
}
