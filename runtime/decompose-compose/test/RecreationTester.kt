package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi

/**
 * Hosts content in a [SaveableStateRegistry] of its own. [recreate] saves the state of the content, disposes it and
 * composes it again from the saved state, as Android does when it creates an activity again.
 */
// Compose's StateRestorationTester does the same, but throws NotImplementedError on JVM and iOS.
@OptIn(ExperimentalTestApi::class)
internal class RecreationTester(private val test: ComposeUiTest) {
    private var savedValues: Map<String, List<Any?>> = emptyMap()
    private var registry: SaveableStateRegistry? = null
    private var isShown by mutableStateOf(true)

    fun setContent(content: @Composable () -> Unit) {
        test.setContent {
            // The content stays at the same place in the composition, so its rememberSaveable keys stay the same.
            if (isShown) {
                val registry = remember {
                    SaveableStateRegistry(restoredValues = savedValues, canBeSaved = { true }).also { registry = it }
                }
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content)
            }
        }
    }

    fun recreate() {
        savedValues = test.runOnIdle { checkNotNull(registry).performSave() }
        isShown = false
        test.waitForIdle()
        isShown = true
        test.waitForIdle()
    }
}
