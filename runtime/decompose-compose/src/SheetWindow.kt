package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * A value of the window that shows a sheet, read where the sheet is called, that the sheet's own window provides to
 * its content; `null` where the sheet needs none.
 */
@Composable
internal expect fun hostWindowLocal(): ProvidedValue<*>?

/**
 * Calls [onBack] on back in the sheet's window while [enabled], where compose-unstyled's sheet doesn't close on back
 * itself. Handlers that the sheet's content registers with [rememberWindowBackHandler] come first.
 */
@Composable
internal expect fun SheetBackHandler(enabled: Boolean, onBack: () -> Unit)
