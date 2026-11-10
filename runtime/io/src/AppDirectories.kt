package tech.annexflow.lvm.io

import kotlinx.io.files.Path

/**
 * The app's own directories. Each platform has a factory named after this type: `AppDirectories(context)` on Android,
 * `AppDirectories()` on iOS and `AppDirectories(appId)` on JVM. Common code gets an instance from the app, for example
 * through dependency injection.
 *
 * Reading a directory creates it if it is missing, so a read touches the disk. Read directories off the main thread.
 */
public interface AppDirectories {

    /** Files the app can recreate. The system may delete them when space runs low. */
    public val cache: Path

    /** Files the app keeps until it deletes them or is uninstalled. The user doesn't see them. */
    public val data: Path
}
