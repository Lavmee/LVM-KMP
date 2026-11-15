package tech.annexflow.lvm.logger.file

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Opens log files whose writes fail on request, as writes to a full disk or a locked iOS device do. */
@OptIn(ExperimentalAtomicApi::class)
class FailingSinks {
    private val writesToFail = AtomicInt(0)
    private val failures = AtomicInt(0)

    val failed: Boolean get() = failures.load() > 0

    fun failNextWrite() {
        writesToFail.store(1)
    }

    fun open(path: Path): RawSink = FailingSink(SystemFileSystem.sink(path, append = true))

    private inner class FailingSink(private val sink: RawSink) : RawSink {
        override fun write(source: Buffer, byteCount: Long) {
            // Only the writer's thread writes, so the check and the decrement don't race.
            if (writesToFail.load() > 0) {
                writesToFail.decrementAndFetch()
                failures.incrementAndFetch()
                throw IOException("the write failed")
            }
            sink.write(source, byteCount)
        }

        override fun flush() = sink.flush()

        override fun close() = sink.close()
    }
}
