package tech.annexflow.lvm.logger.file.internal

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking

// Apple platforms have no thread interrupts.
internal actual fun <E> SendChannel<E>.sendUninterruptibly(element: E) {
    trySendBlocking(element)
}
