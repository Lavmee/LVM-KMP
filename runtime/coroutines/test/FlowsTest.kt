package tech.annexflow.lvm.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class FlowsTest {

    @Test
    fun `mapList transforms every element of every list`() = runTest {
        val lists = flowOf(listOf(1, 2), emptyList(), listOf(3)).mapList { it * 10 }.toList()

        assertEquals(listOf(listOf(10, 20), emptyList(), listOf(30)), lists)
    }

    @Test
    fun `collectCatching returns the failure of the flow`() = runTest {
        val failure = IllegalStateException()
        val collected = mutableListOf<Int>()

        val result = flow {
            emit(1)
            throw failure
        }.collectCatching { collected += it }

        assertEquals(listOf(1), collected)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `collectCatching returns success when the flow completes`() = runTest {
        assertTrue(flowOf(1, 2).collectCatching { }.isSuccess)
    }
}
