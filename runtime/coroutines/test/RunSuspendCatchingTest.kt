package tech.annexflow.lvm.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

class RunSuspendCatchingTest {

    @Test
    fun `returns the value as a success`() = runTest {
        assertEquals(Result.success(42), runSuspendCatching { 42 })
    }

    @Test
    fun `returns a thrown exception as a failure`() = runTest {
        val failure = IllegalStateException()

        assertSame(failure, runSuspendCatching<Int> { throw failure }.exceptionOrNull())
    }

    @Test
    fun `rethrows cancellation`() = runTest {
        assertFailsWith<CancellationException> {
            runSuspendCatching<Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `returns a timeout inside the block as a failure`() = runTest {
        val result = runSuspendCatching { withTimeout(100) { delay(1_000) } }

        assertIs<TimeoutCancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `rethrows the timeout of an enclosing withTimeout`() = runTest {
        var continued = false

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) {
                runSuspendCatching { delay(1_000) }
                continued = true
            }
        }
        assertFalse(continued)
    }
}
