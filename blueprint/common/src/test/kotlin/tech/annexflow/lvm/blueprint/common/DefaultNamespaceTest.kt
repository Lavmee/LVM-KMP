package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.gradle.api.InvalidUserDataException
import org.gradle.testfixtures.ProjectBuilder

class DefaultNamespaceTest {

    @Test
    fun `joins group and module path and replaces hyphens`() {
        assertEquals("com.example.feature.user_profile", defaultNamespace("com.example", ":feature:user-profile"))
    }

    @Test
    fun `requires a group`() {
        assertFailsWith<InvalidUserDataException> { defaultNamespace("", ":lib") }
    }

    @Test
    fun `computes the group gradle assigns implicitly`() {
        assertEquals("", gradleImplicitGroup("my-app", ":"))
        assertEquals("my-app", gradleImplicitGroup("my-app", ":lib"))
        assertEquals("my-app.feature", gradleImplicitGroup("my-app", ":feature:user-profile"))
    }

    @Test
    fun `nested module without its own group gets no derived namespace`() {
        val root = ProjectBuilder.builder().withName("my-app").build()
        val feature = ProjectBuilder.builder().withName("feature").withParent(root).build()
        val userProfile = ProjectBuilder.builder().withName("user-profile").withParent(feature).build()

        val error = assertFailsWith<Exception> { userProfile.lvmAndroid().namespace.get() }
        val invalid = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<InvalidUserDataException>().firstOrNull()
        assertNotNull(invalid)
        assertContains(invalid.message.orEmpty(), "set group")

        userProfile.group = "com.example"
        assertEquals("com.example.feature.user_profile", userProfile.lvmAndroid().namespace.get())
    }
}
