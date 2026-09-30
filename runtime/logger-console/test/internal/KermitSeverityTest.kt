package tech.annexflow.lvm.logger.console.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.annexflow.lvm.logger.Severity

class KermitSeverityTest {

    @Test
    fun `every severity maps to the Kermit severity of the same name`() {
        for (severity in Severity.entries) {
            assertEquals(severity.name, severity.asKermit().name.uppercase())
        }
    }
}
