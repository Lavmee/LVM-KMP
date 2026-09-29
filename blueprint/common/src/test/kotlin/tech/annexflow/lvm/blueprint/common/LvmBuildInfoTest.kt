package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LvmBuildInfoTest {

    @Test
    fun `every tested version satisfies its minimum`() {
        val versions = mapOf(
            "Gradle" to (LvmBuildInfo.TESTED_GRADLE to LvmBuildInfo.MIN_GRADLE),
            "Kotlin" to (LvmBuildInfo.TESTED_KOTLIN to LvmBuildInfo.MIN_KOTLIN),
            "AGP" to (LvmBuildInfo.TESTED_AGP to LvmBuildInfo.MIN_AGP),
        )
        for ((tool, testedAndMinimum) in versions) {
            val (tested, minimum) = testedAndMinimum
            assertEquals(
                Support.SUPPORTED,
                LvmCompatibility.support(tested, minimum, tested),
                "$tool: tested version $tested against minimum $minimum",
            )
        }
    }

    @Test
    fun `every version constant is a numeric dotted version`() {
        val versions = mapOf(
            "VERSION" to LvmBuildInfo.VERSION,
            "TESTED_GRADLE" to LvmBuildInfo.TESTED_GRADLE,
            "TESTED_KOTLIN" to LvmBuildInfo.TESTED_KOTLIN,
            "TESTED_AGP" to LvmBuildInfo.TESTED_AGP,
            "MIN_GRADLE" to LvmBuildInfo.MIN_GRADLE,
            "MIN_KOTLIN" to LvmBuildInfo.MIN_KOTLIN,
            "MIN_AGP" to LvmBuildInfo.MIN_AGP,
        )
        for ((name, version) in versions) {
            assertTrue(VERSION.matches(version), "$name is not a numeric dotted version: <$version>")
        }
    }

    @Test
    fun `the compose rules are a group, name and version coordinate`() {
        val coordinate = LvmBuildInfo.DETEKT_COMPOSE_RULES
        val parts = coordinate.split(':')
        assertEquals(3, parts.size, "Not a group:name:version coordinate: <$coordinate>")
        val (group, name, version) = parts
        assertTrue(COORDINATE_PART.matches(group), "Invalid group in <$coordinate>")
        assertTrue(COORDINATE_PART.matches(name), "Invalid name in <$coordinate>")
        assertTrue(VERSION.matches(version), "Invalid version in <$coordinate>")
    }

    private companion object {
        val VERSION = Regex("""^\d+(\.\d+){1,2}([-.].+)?$""")
        val COORDINATE_PART = Regex("""^[A-Za-z0-9_][A-Za-z0-9_.-]*$""")
    }
}
