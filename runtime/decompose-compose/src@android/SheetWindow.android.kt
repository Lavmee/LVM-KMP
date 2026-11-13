package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration

// The sheet's window has its own configuration. On API 24 to 32, AppCompat tells only the activity's window about a
// language changed in place, so without the host's configuration an open sheet would keep the old language.
@Composable
internal actual fun hostWindowLocal(): ProvidedValue<*>? = LocalConfiguration provides LocalConfiguration.current

// compose-unstyled closes the sheet on the back of its window.
@Composable
internal actual fun SheetBackHandler(enabled: Boolean, onBack: () -> Unit): Unit = Unit
