package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SetsTest {

    @Test
    fun `switchElement removes a present element and adds a missing one`() {
        assertEquals(setOf(1), setOf(1, 2).switchElement(2))
        assertEquals(setOf(1, 2), setOf(1).switchElement(2))
    }
}
