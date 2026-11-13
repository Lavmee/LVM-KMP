package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackEvent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalDecomposeApi::class)
class StackAnimationsTest {

    @Test
    fun `the selector sees each child that changes with the other child and its direction`() = runComposeUiTest {
        var stack by mutableStateOf(stackOf("a"))
        val contexts = mutableSetOf<SelectorContext<String, String>>()
        setContent {
            TestChildStack(stack, stackAnimation { contexts += this; null })
        }

        stack = stackOf("a", "b")
        waitForIdle()

        assertEquals(
            setOf(
                SelectorContext(created("b"), created("a"), Direction.ENTER_FRONT, isPredictiveBack = false),
                SelectorContext(created("a"), created("b"), Direction.EXIT_BACK, isPredictiveBack = false),
            ),
            contexts,
        )
    }

    @Test
    fun `the selector knows when a predictive back gesture drives the change`() = runComposeUiTest {
        val backDispatcher = BackDispatcher()
        val contexts = mutableSetOf<SelectorContext<String, String>>()
        val animation = stackAnimation<String, String>(
            predictiveBackParams = { PredictiveBackParams(backHandler = backDispatcher, onBack = {}) },
        ) {
            contexts += this
            null
        }
        setContent { TestChildStack(stackOf("a", "b"), animation) }

        runOnIdle {
            backDispatcher.startPredictiveBack(BackEvent(progress = 0.1F))
            backDispatcher.progressPredictiveBack(BackEvent(progress = 0.2F))
        }
        waitForIdle()

        assertEquals(
            setOf(
                SelectorContext(created("a"), created("b"), Direction.ENTER_BACK, isPredictiveBack = true),
                SelectorContext(created("b"), created("a"), Direction.EXIT_FRONT, isPredictiveBack = true),
            ),
            contexts,
        )
    }
}
