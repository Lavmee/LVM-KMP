package tech.annexflow.lvm.coroutines

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class StateFlowsTest {

    @Test
    fun `the value is computed from the current source values without a collector`() {
        val first = MutableStateFlow(1)
        val second = MutableStateFlow(10)
        val sum = combineState(first, second) { a, b -> a + b }

        assertEquals(11, sum.value)
        first.value = 2
        assertEquals(12, sum.value)
    }

    @Test
    fun `every arity passes its sources to the transform in order`() = runTest {
        val a = MutableStateFlow("a")
        val b = MutableStateFlow("b")
        val c = MutableStateFlow("c")
        val d = MutableStateFlow("d")
        val e = MutableStateFlow("e")
        val combined = listOf(
            combineState(a) { x -> x },
            combineState(a, b) { x, y -> x + y },
            combineState(a, b, c) { x, y, z -> x + y + z },
            combineState(a, b, c, d) { x, y, z, w -> x + y + z + w },
            combineState(a, b, c, d, e) { x, y, z, w, v -> x + y + z + w + v },
        )
        val expected = listOf("a", "ab", "abc", "abcd", "abcde")

        assertEquals(expected, combined.map { it.value })
        assertEquals(expected, combined.map { it.first() })
    }

    @Test
    fun `collectors get the current value and then every change of the result`() = runTest {
        val first = MutableStateFlow(1)
        val second = MutableStateFlow(10)

        combineState(first, second) { a, b -> a + b }.test {
            assertEquals(11, awaitItem())
            first.value = 2
            assertEquals(12, awaitItem())
            second.value = 20
            assertEquals(22, awaitItem())
        }
    }

    @Test
    fun `a change that maps to an equal result emits nothing`() = runTest {
        val source = MutableStateFlow(1)

        source.mapState { it % 2 }.test {
            assertEquals(1, awaitItem())
            source.value = 3
            expectNoEvents()
            source.value = 4
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `chained mapState follows the original source`() = runTest {
        val source = MutableStateFlow(1)
        val chained = source.mapState { it * 2 }.mapState { it + 1 }

        assertEquals(3, chained.value)
        chained.test {
            assertEquals(3, awaitItem())
            source.value = 5
            assertEquals(11, awaitItem())
        }
    }
}
