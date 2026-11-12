package tech.annexflow.lvm.decompose

import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigatorsTest {

    @Test
    fun `replaceAll replaces the whole stack and completes`() {
        val navigation = StackNavigation<String>()
        val stack = rootContext().childStack(
            source = navigation,
            serializer = null,
            initialStack = { listOf("list", "details") },
            childFactory = { configuration, _ -> configuration },
        )
        var completed = false

        navigation.replaceAll("home", "settings") { completed = true }

        assertEquals(listOf("home", "settings"), stack.value.items.map { it.configuration })
        assertTrue(completed)
    }

    @Test
    fun `activate opens a slot without a configuration and completes`() {
        val navigation = SlotNavigation<Unit>()
        val slot = rootContext().childSlot(
            source = navigation,
            serializer = null,
            childFactory = { _, _ -> "dialog" },
        )
        var completed = false

        navigation.activate { completed = true }

        assertNotNull(slot.value.child)
        assertTrue(completed)
    }
}
