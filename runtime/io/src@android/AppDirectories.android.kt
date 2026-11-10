package tech.annexflow.lvm.io

import android.content.Context
import kotlinx.io.files.Path

/**
 * The directories of the app that [context] belongs to: [AppDirectories.cache] is [Context.getCacheDir] and
 * [AppDirectories.data] is [Context.getFilesDir]. Only the application context is kept, so passing an Activity is
 * safe.
 */
public fun AppDirectories(context: Context): AppDirectories =
    ContextAppDirectories(context.applicationContext ?: context)

private class ContextAppDirectories(private val context: Context) : AppDirectories {

    // Android creates the directories on every call, so a cache cleared while the app runs comes back.
    override val cache: Path get() = Path(context.cacheDir.path)

    override val data: Path get() = Path(context.filesDir.path)
}
