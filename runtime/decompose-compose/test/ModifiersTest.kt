package tech.annexflow.lvm.decompose.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ModifiersTest {

    @Test
    fun `offsetYFactor moves the content by a factor of its height and keeps its place in the layout`() =
        runComposeUiTest {
            setContent {
                Column {
                    Box(Modifier.offsetYFactor(0.5F).size(100.dp, 60.dp).testTag("moved"))
                    Box(Modifier.size(100.dp, 60.dp).testTag("next"))
                }
            }

            onNodeWithTag("moved").assertTopPositionInRootIsEqualTo(30.dp)
            onNodeWithTag("next").assertTopPositionInRootIsEqualTo(60.dp)
        }
}
