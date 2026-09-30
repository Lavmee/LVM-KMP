package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvictingMapTest {

    @Test
    fun `put evicts the entries inserted first`() {
        val map = EvictingMap<String, Int>(maxSize = 2)
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3

        assertEquals(listOf("b", "c"), map.keys.toList())
    }

    @Test
    fun `putAll trims the map to its size`() {
        val map = EvictingMap<String, Int>(maxSize = 2)
        map.putAll(mapOf("a" to 1, "b" to 2, "c" to 3))

        assertEquals(mapOf("b" to 2, "c" to 3), map.toMap())
    }

    @Test
    fun `replacing a value keeps the key's insertion position`() {
        val map = EvictingMap<String, Int>(maxSize = 2)
        map["a"] = 1
        map["b"] = 2
        map["a"] = 10
        map["c"] = 3

        assertEquals(mapOf("b" to 2, "c" to 3), map.toMap())
    }

    @Test
    fun `a size below one is rejected`() {
        assertFailsWith<IllegalArgumentException> { EvictingMap<String, Int>(maxSize = 0) }
    }
}
