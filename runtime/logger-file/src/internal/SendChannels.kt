package tech.annexflow.lvm.logger.file.internal

import kotlinx.coroutines.channels.SendChannel

/**
 * Sends [element] like `trySendBlocking`, but never throws and keeps the calling thread's interrupt status. The send
 * waits even on an interrupted thread; an interrupt that arrives while it waits drops [element].
 */
internal expect fun <E> SendChannel<E>.sendUninterruptibly(element: E)
