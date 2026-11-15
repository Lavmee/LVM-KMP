package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import org.gradle.testfixtures.ProjectBuilder

class LvmBuildValuesTest {

    @Test
    fun `every value reads its own key`() {
        val project = ProjectBuilder.builder().build()
        val forwarded = mapOf(
            "lvm.android.compileSdk" to "31",
            "lvm.android.minSdk" to "32",
            "lvm.android.targetSdk" to "33",
            "lvm.android.testInstrumentationRunner" to "com.example.Runner",
            "lvm.android.hostTests" to "false",
            "lvm.jvm.target" to "11",
            "lvm.kotlin.warningsAsErrors" to "true",
            "lvm.kotlin.optIns" to "a.OptIn,b.OptIn",
            "lvm.kotlin.freeCompilerArgs" to "-Xa",
            "lvm.kotlin.explicitApi" to "true",
            "lvm.kotlin.testDependencies" to "false",
            "lvm.kotlin.flatLayout" to "true",
        )
        forwarded.forEach { (key, value) -> project.extensions.extraProperties[key] = value }

        val values = project.lvmValues
        assertEquals(31, values.android.compileSdk.get())
        assertEquals(32, values.android.minSdk.get())
        assertEquals(33, values.android.targetSdk.get())
        assertEquals("com.example.Runner", values.android.testInstrumentationRunner.get())
        assertEquals(false, values.android.hostTests.get())
        assertEquals(11, values.jvm.target.get())
        assertEquals(true, values.kotlin.warningsAsErrors.get())
        assertEquals(listOf("a.OptIn", "b.OptIn"), values.kotlin.optIns.get())
        assertEquals(listOf("-Xa"), values.kotlin.freeCompilerArgs.get())
        assertEquals(true, values.kotlin.explicitApi.get())
        assertEquals(false, values.kotlin.testDependencies.get())
        assertEquals(true, values.kotlin.flatLayout.get())
    }

    @Test
    fun `an unset value is its own default`() {
        val values = ProjectBuilder.builder().build().lvmValues
        assertEquals(LvmDefaults.ANDROID_COMPILE_SDK, values.android.compileSdk.get())
        assertEquals(LvmDefaults.ANDROID_MIN_SDK, values.android.minSdk.get())
        assertEquals(LvmDefaults.ANDROID_TARGET_SDK, values.android.targetSdk.get())
        assertEquals(LvmDefaults.ANDROID_TEST_INSTRUMENTATION_RUNNER, values.android.testInstrumentationRunner.get())
        assertEquals(LvmDefaults.ANDROID_HOST_TESTS, values.android.hostTests.get())
        assertEquals(LvmDefaults.JVM_TARGET, values.jvm.target.get())
        assertEquals(LvmDefaults.KOTLIN_WARNINGS_AS_ERRORS, values.kotlin.warningsAsErrors.get())
        assertEquals(LvmDefaults.KOTLIN_OPT_INS, values.kotlin.optIns.get())
        assertEquals(LvmDefaults.KOTLIN_FREE_COMPILER_ARGS, values.kotlin.freeCompilerArgs.get())
        assertEquals(LvmDefaults.KOTLIN_EXPLICIT_API, values.kotlin.explicitApi.get())
        assertEquals(LvmDefaults.KOTLIN_TEST_DEPENDENCIES, values.kotlin.testDependencies.get())
        assertEquals(LvmDefaults.KOTLIN_FLAT_LAYOUT, values.kotlin.flatLayout.get())
    }
}
