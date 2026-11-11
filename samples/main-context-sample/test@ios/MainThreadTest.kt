package tech.annexflow.lvm.samples.maincontext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode

class MainThreadTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a push from a background coroutine runs on the main thread`() {
        // Tests run on the main thread, which then runs the main queue below while the coroutine works.
        assertTrue(NSThread.isMainThread)
        var pushedOnMainThread: Boolean? = null
        val navigator = Navigator { pushedOnMainThread = NSThread.isMainThread }

        val push = CoroutineScope(Dispatchers.Default).async { navigator.pushLoaded { "details" } }
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (!push.isCompleted && deadline.hasNotPassedNow()) {
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }

        push.getCompleted()
        assertEquals(true, pushedOnMainThread)
    }
}
