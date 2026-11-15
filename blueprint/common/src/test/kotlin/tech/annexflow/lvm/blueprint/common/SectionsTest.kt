package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.gradle.api.InvalidUserCodeException
import org.gradle.testfixtures.ProjectBuilder

class SectionsTest {

    @Test
    fun `an accessor returns the section a plugin created`() {
        val project = ProjectBuilder.builder().build()
        assertFailsWith<InvalidUserCodeException> { project.lvm.android }
        assertFailsWith<InvalidUserCodeException> { project.lvm.jvm }
        assertFailsWith<InvalidUserCodeException> { project.lvm.kotlin }

        val android = project.lvmAndroid()
        val jvm = project.lvmJvm()
        val kotlin = project.lvmKotlin()

        assertSame(android, project.lvm.android)
        assertSame(jvm, project.lvm.jvm)
        assertSame(kotlin, project.lvm.kotlin)
    }
}
