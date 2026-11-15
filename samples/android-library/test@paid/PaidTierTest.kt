package tech.annexflow.lvm.samples.android.library

import kotlin.test.Test
import kotlin.test.assertContains

class PaidTierTest {

    @Test
    fun `the paid flavor greets with the paid tier`() {
        assertContains(greeting(), "paid tier")
    }
}
