package tech.annexflow.lvm.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class IterablesTest {

    private class Failure(val value: Int) : Exception()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `mapAsync runs the transforms concurrently and keeps the order`() = runTest {
        val results = listOf(3, 1, 2).mapAsync { seconds ->
            delay(seconds * 1_000L)
            seconds * 10
        }

        assertEquals(listOf(30, 10, 20), results)
        assertEquals(3_000, currentTime)
    }

    @Test
    fun `mapAsync rethrows a failing transform`() = runTest {
        val failure = assertFailsWith<Failure> {
            listOf(1, 2).mapAsync { value -> if (value == 2) throw Failure(value) else value }
        }

        assertEquals(2, failure.value)
    }
}
