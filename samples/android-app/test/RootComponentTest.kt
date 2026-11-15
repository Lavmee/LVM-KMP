package tech.annexflow.lvm.samples.android

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import tech.annexflow.lvm.decompose.PreviewAppComponentContext

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selecting a color shows its details, and back shows the colors again`() {
        val root = RootComponent(PreviewAppComponentContext())
        val colors = assertIs<RootComponent.Child.Colors>(root.stack.value.active.instance)

        colors.onSelect("Green")
        val details = assertIs<RootComponent.Child.Details>(root.stack.value.active.instance)
        assertEquals("Green", details.color)

        details.onBack()
        assertIs<RootComponent.Child.Colors>(root.stack.value.active.instance)
    }
}
