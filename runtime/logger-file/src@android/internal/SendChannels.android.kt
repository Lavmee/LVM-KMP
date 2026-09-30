package tech.annexflow.lvm.logger.file.internal

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking

internal actual fun <E> SendChannel<E>.sendUninterruptibly(element: E) {
    // trySendBlocking throws on an interrupted thread and clears its interrupt, and a logging call must do neither.
    var interrupted = Thread.interrupted()
    try {
        trySendBlocking(element)
    } catch (e: InterruptedException) {
        interrupted = true
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}
