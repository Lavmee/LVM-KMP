package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LvmCompatibilityTest {

    @Test
    fun `compares dotted versions numerically and ignores qualifiers`() {
        assertTrue(LvmCompatibility.compare("2.4.20", "2.4.3") > 0)
        assertEquals(0, LvmCompatibility.compare("9.4", "9.4.0"))
        assertEquals(0, LvmCompatibility.compare("2.5.0-Beta1", "2.5.0"))
        assertTrue(LvmCompatibility.compare("8.13.0", "9.0.0") < 0)
    }

    @Test
    fun `classifies support`() {
        assertEquals(Support.TOO_OLD, LvmCompatibility.support("2.3.20", minimum = "2.4.0", tested = "2.4.20"))
        assertEquals(Support.SUPPORTED, LvmCompatibility.support("2.4.20", minimum = "2.4.0", tested = "2.4.20"))
        assertEquals(Support.NEWER_THAN_TESTED, LvmCompatibility.support("2.5.0", minimum = "2.4.0", tested = "2.4.20"))
    }
}
