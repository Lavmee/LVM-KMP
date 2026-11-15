package tech.annexflow.lvm.samples.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import tech.annexflow.lvm.decompose.compose.slide
import tech.annexflow.lvm.decompose.compose.stackAnimation

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    ChildStack(
        stack = component.stack,
        // The window draws edge to edge, so the content keeps clear of the system bars.
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        animation = stackAnimation { slide(invert = false) },
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Colors -> Colors(instance)
            is RootComponent.Child.Details -> Details(instance)
        }
    }
}

@Composable
private fun Colors(child: RootComponent.Child.Colors) {
    Column(Modifier.padding(16.dp)) {
        child.colors.forEach { color ->
            BasicText(color, Modifier.clickable { child.onSelect(color) }.padding(8.dp))
        }
    }
}

@Composable
private fun Details(child: RootComponent.Child.Details) {
    BasicText("${child.color} is selected. Tap to go back.", Modifier.clickable(onClick = child.onBack).padding(16.dp))
}
