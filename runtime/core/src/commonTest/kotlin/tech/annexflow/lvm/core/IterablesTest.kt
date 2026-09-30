package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IterablesTest {

    private val items: List<Any> = listOf("a", 1, "b", 2)

    @Test
    fun `instance lookups find the first and last element of a type`() {
        assertEquals("a", items.firstOrNullIsInstance<String>())
        assertEquals(2, items.lastOrNullIsInstance<Int>())
        assertEquals("a", items.firstIsInstance<String>())
        assertEquals("b", items.lastIsInstance<String>())
        assertNull(items.firstOrNullIsInstance<Double>())
        assertNull(items.lastOrNullIsInstance<Double>())
    }

    @Test
    fun `strict instance lookups fail when no element has the type`() {
        assertFailsWith<NoSuchElementException> { items.firstIsInstance<Double>() }
        assertFailsWith<NoSuchElementException> { items.lastIsInstance<Double>() }
    }

    @Test
    fun `findOrFirstOrNull falls back to the first element`() {
        assertEquals(3, listOf(1, 3, 5).findOrFirstOrNull { it > 2 })
        assertEquals(1, listOf(1, 3, 5).findOrFirstOrNull { it > 9 })
        assertNull(emptyList<Int>().findOrFirstOrNull { it > 2 })
    }

    @Test
    fun `findOrFirstOrNull returns a matching null element`() {
        assertNull(listOf(1, null).findOrFirstOrNull { it == null })
    }

    @Test
    fun `findOrFirstOrNull walks the elements only once`() {
        val once = sequenceOf(1, 3).constrainOnce().asIterable()

        assertEquals(1, once.findOrFirstOrNull { it > 9 })
    }
}
