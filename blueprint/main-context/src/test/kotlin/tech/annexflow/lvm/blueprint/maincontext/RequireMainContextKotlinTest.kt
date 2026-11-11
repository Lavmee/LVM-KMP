package tech.annexflow.lvm.blueprint.maincontext

import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo

class RequireMainContextKotlinTest {

    @Test
    fun `accepts only the Kotlin version the compiler plugin was built with`() {
        val (major, minor, patch) = LvmBuildInfo.TESTED_KOTLIN.split('.')
        requireMainContextKotlin(LvmBuildInfo.TESTED_KOTLIN)

        assertFailsWith<GradleException> { requireMainContextKotlin("$major.$minor.${patch.toInt() + 1}") }
        assertFailsWith<GradleException> { requireMainContextKotlin("$major.${minor.toInt() + 1}.0") }
    }
}
