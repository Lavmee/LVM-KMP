package tech.annexflow.lvm.storage.internal

import okio.FileSystem

/** The file system of the device. okio declares `FileSystem.SYSTEM` on each platform, not in common code. */
internal expect val systemFileSystem: FileSystem
