package tech.annexflow.lvm.samples.android.library

import kotlin.test.Test
import kotlin.test.assertContains

class GreetingTest {

    @Test
    fun `the greeting names the tier of the build`() {
        assertContains(greeting(), "$TIER tier")
    }
}
