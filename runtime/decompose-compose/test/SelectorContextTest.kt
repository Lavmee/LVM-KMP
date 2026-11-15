package tech.annexflow.lvm.decompose.compose

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorContextTest {

    @Test
    fun `animate calls the lambda of each side whose instance has the type`() {
        val context = selectorContext(child = "text", otherChild = 1)
        val calls = mutableListOf<String>()

        context.animate<String>(child = { calls += "child is a String" }, other = { calls += "other is a String" })
        context.animate<Int>(child = { calls += "child is an Int" }, other = { calls += "other is an Int" })

        assertEquals(listOf("child is a String", "other is an Int"), calls)
    }

    @Test
    fun `animate of two types calls child or other only for a change between the two`() {
        fun calls(child: Any, otherChild: Any): List<String> {
            val calls = mutableListOf<String>()
            selectorContext(child, otherChild)
                .animate<String, Int>(child = { calls += "child" }, other = { calls += "other" })
            return calls
        }

        assertEquals(listOf("child"), calls(child = "text", otherChild = 1))
        assertEquals(listOf("other"), calls(child = 1, otherChild = "text"))
        assertEquals(emptyList(), calls(child = "text", otherChild = "more text"))
        assertEquals(emptyList(), calls(child = 1.5, otherChild = 1))
    }

    @Test
    fun `animate of one type twice calls both lambdas when both children have it`() {
        val calls = mutableListOf<String>()

        selectorContext(child = "text", otherChild = "more text")
            .animate<String, String>(child = { calls += "child" }, other = { calls += "other" })

        assertEquals(listOf("child", "other"), calls)
    }
}

private fun selectorContext(child: Any, otherChild: Any) = SelectorContext(
    child = Child.Created(configuration = child, instance = child),
    otherChild = Child.Created(configuration = otherChild, instance = otherChild),
    direction = Direction.ENTER_FRONT,
    isPredictiveBack = false,
)
