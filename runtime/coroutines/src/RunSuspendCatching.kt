package tech.annexflow.lvm.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] and returns its value, or the exception it threw, as a [Result].
 *
 * Unlike [runCatching], it rethrows [CancellationException], so cancellation keeps propagating. The
 * [TimeoutCancellationException] of a `withTimeout` inside [block] is returned as a failure, so it reads as a failed
 * call. The timeout of an enclosing `withTimeout` is rethrown, because it cancels the calling coroutine.
 */
public suspend inline fun <R> runSuspendCatching(
    block: () -> R,
): Result<R> {
    return try {
        Result.success(block())
    } catch (t: TimeoutCancellationException) {
        // When an enclosing withTimeout fires, suspension points inside block throw its timeout, which lands here
        // too. That timeout has cancelled this coroutine, so ensureActive rethrows it.
        currentCoroutineContext().ensureActive()
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
