package tech.annexflow.lvm.samples.smoke

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {

    @Test
    fun greetingNamesThePlatform() {
        assertEquals("Hello from ${platformName()}", greeting())
    }
}
