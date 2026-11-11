package tech.annexflow.lvm.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.CorruptionHandler
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.Path
import okio.Path.Companion.toPath
import tech.annexflow.lvm.io.AppDirectories
import tech.annexflow.lvm.storage.internal.systemFileSystem

/**
 * A [DataStore] that keeps its value in the file [fileName] in the data directory of [directories]: `filesDir` on
 * Android, `Library/Application Support` on iOS and the data folder of `AppDirectories(appId)` on JVM. [serializer]
 * reads and writes the file, for example one from [jsonSerializer] or [binarySerializer].
 *
 * Building the store doesn't touch the disk. The store reads the data directory and opens the file in [context], when
 * its value is first read or updated, so it can be built on the main thread.
 *
 * When [serializer] fails with a [CorruptionException], [corruptionHandler] produces the value instead, and the store
 * writes it to the file. By default that is the serializer's default value. [migrations] run before the first value
 * is read.
 *
 * Build one store per file and share it: while a store is active, a second store on the same file fails on its first
 * read or update. A store stays active until the `Job` of [context] completes.
 *
 * @throws IllegalArgumentException if [fileName] is not a single file name: blank, containing `/` or `\`, or `.` or
 * `..`.
 */
// The file's place and serializer, then the three options of DataStore.Builder; fewer would take one of them away.
@Suppress("LongParameterList")
public fun <T> createDataStore(
    directories: AppDirectories,
    fileName: String,
    serializer: OkioSerializer<T>,
    corruptionHandler: CorruptionHandler<T> = ReplaceFileCorruptionHandler { serializer.defaultValue },
    migrations: List<DataMigration<T>> = emptyList(),
    context: CoroutineContext = Dispatchers.IO + SupervisorJob(),
): DataStore<T> {
    require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName && fileName != "." && fileName != "..") {
        "fileName must be a single file name, such as settings.json, but was \"$fileName\""
    }
    return createDataStore(
        path = { Path(directories.data, fileName) },
        serializer = serializer,
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        context = context,
    )
}

/**
 * A [DataStore] that keeps its value in the file at [path], which must be absolute. Missing parent directories are
 * created. [serializer] reads and writes the file, for example one from [jsonSerializer] or [binarySerializer].
 *
 * Building the store doesn't touch the disk. The store calls [path] and opens the file in [context], when its value is
 * first read or updated, so [path] may do disk work itself, such as reading a directory of `AppDirectories`.
 *
 * When [serializer] fails with a [CorruptionException], [corruptionHandler] produces the value instead, and the store
 * writes it to the file. By default that is the serializer's default value. [migrations] run before the first value
 * is read.
 *
 * Build one store per file and share it: while a store is active, a second store on the same file fails on its first
 * read or update. A store stays active until the `Job` of [context] completes.
 */
public fun <T> createDataStore(
    path: () -> Path,
    serializer: OkioSerializer<T>,
    corruptionHandler: CorruptionHandler<T> = ReplaceFileCorruptionHandler { serializer.defaultValue },
    migrations: List<DataMigration<T>> = emptyList(),
    context: CoroutineContext = Dispatchers.IO + SupervisorJob(),
): DataStore<T> {
    val storage = OkioStorage(
        fileSystem = systemFileSystem,
        serializer = serializer,
        producePath = { path().toString().toPath() },
    )
    return DataStore.Builder(storage, context)
        .setCorruptionHandler(corruptionHandler)
        .addMigrations(migrations)
        .build()
}
