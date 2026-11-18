package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.slot.ChildSlot

internal fun slotOf(instance: String): ChildSlot<String, String> = slotOf(configuration = instance, instance = instance)

internal fun slotOf(configuration: String, instance: String): ChildSlot<String, String> =
    ChildSlot(child = Child.Created(configuration = configuration, instance = instance))

/** Content of a slot's child, tagged with [instance]. */
@Composable
internal fun Content(instance: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(200.dp).testTag(instance))
}
