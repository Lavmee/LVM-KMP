package tech.annexflow.lvm.decompose.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ChildSlotContentTest {

    @Test
    fun `the content shows the child while the slot has one`() = runComposeUiTest {
        var slot by mutableStateOf(ChildSlot<String, String>())
        setContent {
            ChildSlotContent(slot = slot) { instance -> Content(instance) }
        }
        onNodeWithTag("first").assertDoesNotExist()

        slot = slotOf("first")
        waitForIdle()
        onNodeWithTag("first").assertExists()

        slot = ChildSlot()
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
    }

    @Test
    fun `the content follows a slot given as a Value`() = runComposeUiTest {
        val slot = MutableValue(ChildSlot<String, String>())
        setContent {
            ChildSlotContent(slot = slot) { instance -> Content(instance) }
        }
        onNodeWithTag("first").assertDoesNotExist()

        slot.value = slotOf("first")
        waitForIdle()
        onNodeWithTag("first").assertExists()

        slot.value = ChildSlot()
        waitForIdle()
        onNodeWithTag("first").assertDoesNotExist()
    }

    @Test
    fun `a new child doesn't get what the previous child remembered`() = runComposeUiTest {
        var slot by mutableStateOf(slotOf("first"))
        setContent {
            ChildSlotContent(slot = slot) { instance ->
                val rememberedInstance = remember { instance }
                Content(rememberedInstance)
            }
        }

        slot = slotOf("second")
        waitForIdle()

        onNodeWithTag("second").assertExists()
    }

    @Test
    fun `a child created again gets its saved state back`() = runComposeUiTest {
        var instance = "first"
        val recreationTester = RecreationTester(this)
        recreationTester.setContent {
            ChildSlotContent(slot = slotOf(configuration = "config", instance = instance)) { current ->
                val savedInstance = rememberSaveable { current }
                Content(savedInstance)
            }
        }

        // The component is created again for the same configuration, as after process death.
        instance = "second"
        recreationTester.recreate()

        onNodeWithTag("first").assertExists()
    }
}
