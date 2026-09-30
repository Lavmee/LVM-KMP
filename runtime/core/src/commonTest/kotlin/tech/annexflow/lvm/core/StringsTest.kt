package tech.annexflow.lvm.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {

    @Test
    fun `replaceLast replaces only the last occurrence`() {
        assertEquals("1.000,50", "1.000.50".replaceLast('.', ','))
        assertEquals("abcaX", "abcabc".replaceLast("bc", "X"))
    }

    @Test
    fun `replaceLast ignores case only when asked to`() {
        assertEquals("abXa", "abXa".replaceLast('x', 'y'))
        assertEquals("abya", "abXa".replaceLast('x', 'y', ignoreCase = true))
        assertEquals("aY", "aBC".replaceLast("bc", "Y", ignoreCase = true))
    }

    @Test
    fun `replaceLast returns the string unchanged when nothing matches`() {
        assertEquals("abc", "abc".replaceLast('z', 'y'))
        assertEquals("abc", "abc".replaceLast("zz", "y"))
    }
}
