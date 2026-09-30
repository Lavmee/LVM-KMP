@file:Suppress("RedundantSuspendModifier")

package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Runs [block] and returns its value, or the exception it threw, as a [Result].
 *
 * Unlike [runCatching], it rethrows [CancellationException], so cancellation keeps propagating. A
 * [TimeoutCancellationException] is returned as a failure, so a `withTimeout` inside [block] reads as a failed call.
 */
public suspend inline fun <R> runSuspendCatching(
    block: () -> R,
): Result<R> {
    return try {
        Result.success(block())
    } catch (t: TimeoutCancellationException) {
        Result.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (
        // Catching everything is the purpose of this function: it turns any failure into a Result. Cancellation is
        // rethrown by the branch above, because it can't become a Result.
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        Result.failure(e)
    }
}
