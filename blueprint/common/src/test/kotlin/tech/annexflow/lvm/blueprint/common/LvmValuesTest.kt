package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder

class LvmValuesTest {

    @Test
    fun `falls back to the default`() {
        val project = ProjectBuilder.builder().build()
        assertEquals(24, LvmValues(project).int(LvmKeys.ANDROID_MIN_SDK, 24).get())
    }

    @Test
    fun `uses the value forwarded from settings`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties["lvm.android.minSdk"] = "26"
        project.extensions.extraProperties["lvm.kotlin.optIns"] = "a.B,c.D"
        val values = LvmValues(project)
        assertEquals(26, values.int(LvmKeys.ANDROID_MIN_SDK, 24).get())
        assertEquals(listOf("a.B", "c.D"), values.list(LvmKeys.KOTLIN_OPT_INS, emptyList()).get())
    }

    @Test
    fun `reports invalid values with the key`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties["lvm.android.minSdk"] = "abc"
        val error = assertFailsWith<Exception> { LvmValues(project).int(LvmKeys.ANDROID_MIN_SDK, 24).get() }
        assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it.message.orEmpty().contains("lvm.android.minSdk") })
    }

    @Test
    fun `sections take conventions from build-wide values`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties["lvm.jvm.target"] = "17"
        assertEquals(17, project.lvmJvm().target.get())
        assertEquals(37, project.lvmAndroid().compileSdk.get())
        project.lvmJvm().target.set(21)
        assertEquals(21, project.lvmJvm().target.get())
    }
}
