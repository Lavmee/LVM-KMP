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

    @Test
    fun `drops trailing segments in the list and keeps at least one`() {
        val dropped = listOf("ui", "sky", "tweety")

        assertEquals("feature_chat_", defaultResourcePrefix(":feature:chat:ui-sky", dropped))
        assertEquals("feature_chat_", defaultResourcePrefix(":feature:chat:ui", dropped))
        assertEquals("core_", defaultResourcePrefix(":core:ui", dropped))
        assertEquals("core_", defaultResourcePrefix(":core:ui:tweety", dropped))
        assertEquals("feature_account_sign_out_", defaultResourcePrefix(":feature:account-sign-out:ui", dropped))
        assertEquals("core_designsystem_", defaultResourcePrefix(":core:designsystem", dropped))
    }
}
