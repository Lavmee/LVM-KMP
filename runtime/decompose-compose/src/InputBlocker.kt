package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Takes every pointer event on its area, so that nothing drawn under it gets input. */
@Composable
internal fun InputBlocker(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        },
    )
}
