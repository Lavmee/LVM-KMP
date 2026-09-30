package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionsTest {

    @Test
    fun `map applies the transform to the result of every call`() {
        var calls = 0
        val source = { ++calls }
        val mapped = source.map { it * 10 }

        assertEquals(0, calls)
        assertEquals(10, mapped())
        assertEquals(20, mapped())
    }
}
