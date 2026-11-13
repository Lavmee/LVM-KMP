package tech.annexflow.lvm.decompose.compose

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorContextTest {

    @Test
    fun `animate calls the lambda of each side whose instance has the type`() {
        val context = SelectorContext<String, Any>(
            child = Child.Created(configuration = "text", instance = "text"),
            otherChild = Child.Created(configuration = "number", instance = 1),
            direction = Direction.ENTER_FRONT,
            isPredictiveBack = false,
        )
        val calls = mutableListOf<String>()

        context.animate<String>(child = { calls += "child is a String" }, other = { calls += "other is a String" })
        context.animate<Int>(child = { calls += "child is an Int" }, other = { calls += "other is an Int" })

        assertEquals(listOf("child is a String", "other is an Int"), calls)
    }
}
