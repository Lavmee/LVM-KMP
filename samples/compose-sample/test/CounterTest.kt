package tech.annexflow.lvm.samples.compose

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CounterTest {

    @Test
    fun `a click on the counter counts`() = runComposeUiTest {
        setContent { Counter() }

        onNodeWithText("Clicked 0 times").performClick()

        onNodeWithText("Clicked 1 times").assertExists()
    }
}
