package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.InvalidUserDataException

class DefaultNamespaceTest {

    @Test
    fun `joins group and module path and replaces hyphens`() {
        assertEquals("com.example.feature.user_profile", defaultNamespace("com.example", ":feature:user-profile"))
    }

    @Test
    fun `requires a group`() {
        assertFailsWith<InvalidUserDataException> { defaultNamespace("", ":lib") }
    }
}
