package tech.annexflow.lvm.blueprint.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultResourcePrefixTest {

    @Test
    fun `derives the resource prefix from the segments of the module path`() {
        assertEquals("core_ui_", defaultResourcePrefix(":core:ui"))
        assertEquals("feature_chat_history_ui_", defaultResourcePrefix(":feature:chat-history:ui"))
        assertEquals("lib_", defaultResourcePrefix(":lib"))
        assertEquals("design_system_v2_", defaultResourcePrefix(":Design.System:v2"))
    }

    @Test
    fun `the root project has no resource prefix`() {
        assertNull(defaultResourcePrefix(":"))
    }
}
