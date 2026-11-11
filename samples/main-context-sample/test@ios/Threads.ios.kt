package tech.annexflow.lvm.samples.maincontext

import platform.Foundation.NSThread

actual fun currentThread(): Any = NSThread.currentThread
