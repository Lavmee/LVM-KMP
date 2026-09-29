package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.InvalidUserDataException

class LvmValueParsingTest {

    @Test
    fun `parses integers and names the key on error`() {
        assertEquals(26, LvmValueParsing.int(LvmKeys.ANDROID_MIN_SDK, " 26 "))
        val error = assertFailsWith<InvalidUserDataException> { LvmValueParsing.int(LvmKeys.ANDROID_MIN_SDK, "abc") }
        assertContains(error.message!!, "lvm.android.minSdk must be an integer")
    }

    @Test
    fun `parses booleans strictly`() {
        assertEquals(true, LvmValueParsing.boolean(LvmKeys.KOTLIN_EXPLICIT_API, "TRUE"))
        assertEquals(false, LvmValueParsing.boolean(LvmKeys.KOTLIN_EXPLICIT_API, "false"))
        assertFailsWith<InvalidUserDataException> { LvmValueParsing.boolean(LvmKeys.KOTLIN_EXPLICIT_API, "yes") }
    }

    @Test
    fun `parses and joins comma-separated lists`() {
        assertEquals(listOf("a.B", "c.D"), LvmValueParsing.list(" a.B , ,c.D "))
        assertEquals("a.B,c.D", LvmValueParsing.joinList(listOf("a.B", "c.D")))
    }
}
