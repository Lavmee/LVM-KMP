package tech.annexflow.lvm.blueprint.maincontext

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.gradle.api.GradleException
import tech.annexflow.lvm.blueprint.common.LvmBuildInfo

class RequireMainContextKotlinTest {

    private val built = LvmBuildInfo.TESTED_KOTLIN
    private val major = built.split('.')[0].toInt()
    private val minor = built.split('.')[1].toInt()
    private val patch = built.split('.')[2].substringBefore('-').toInt()

    @Test
    fun `accepts the Kotlin version the compiler plugin was built with without a warning`() {
        assertNull(requireMainContextKotlin(built))
    }

    @Test
    fun `accepts another bugfix release of the same Kotlin release with a warning`() {
        val bugfix = if (patch % 10 < 9) patch + 1 else patch - 1

        assertNotNull(requireMainContextKotlin("$major.$minor.$bugfix"))
    }

    @Test
    fun `fails on any other Kotlin release`() {
        val nextRelease = (patch / 10 + 1) * 10

        assertFailsWith<GradleException> { requireMainContextKotlin("$major.$minor.$nextRelease") }
        assertFailsWith<GradleException> { requireMainContextKotlin("$major.${minor + 1}.$patch") }
        assertFailsWith<GradleException> { requireMainContextKotlin("${major + 1}.$minor.$patch") }
    }
}
