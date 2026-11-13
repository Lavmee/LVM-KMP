package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.arkivanov.essenty.backhandler.BackDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackHandlerTest {

    @Test
    fun `back reaches onBack while the handler is in the composition`() = runComposeUiTest {
        val dispatcher = BackDispatcher()
        var isShown by mutableStateOf(true)
        var backCount = 0
        setContent {
            if (isShown) BackHandler(dispatcher) { backCount++ }
        }

        assertTrue(runOnIdle { dispatcher.back() })
        isShown = false
        waitForIdle()

        assertFalse(runOnIdle { dispatcher.back() })
        assertEquals(1, backCount)
    }

    @Test
    fun `back passes a disabled handler`() = runComposeUiTest {
        val dispatcher = BackDispatcher()
        var isEnabled by mutableStateOf(false)
        setContent { BackHandler(dispatcher, isEnabled) {} }

        assertFalse(runOnIdle { dispatcher.back() })
        isEnabled = true
        waitForIdle()

        assertTrue(runOnIdle { dispatcher.back() })
    }

    @Test
    fun `back calls the latest onBack`() = runComposeUiTest {
        val dispatcher = BackDispatcher()
        var screen by mutableStateOf("first")
        val calls = mutableListOf<String>()
        setContent {
            val current = screen
            BackHandler(dispatcher) { calls += current }
        }

        screen = "second"
        waitForIdle()
        runOnIdle { dispatcher.back() }

        assertEquals(listOf("second"), calls)
    }
}
