package tech.annexflow.lvm.blueprint.common

import kotlin.test.Test
import kotlin.test.assertEquals

class LvmBuildInfoTest {

    @Test
    fun `build info matches gradle properties and the version catalog`() {
        assertEquals(System.getProperty("lvm.test.version"), LvmBuildInfo.VERSION)
        assertEquals(System.getProperty("lvm.test.kotlinVersion"), LvmBuildInfo.TESTED_KOTLIN)
        assertEquals(System.getProperty("lvm.test.agpVersion"), LvmBuildInfo.TESTED_AGP)
        assertEquals("ru.kode:detekt-rules-compose:1.4.0", LvmBuildInfo.DETEKT_COMPOSE_RULES)
    }
}
