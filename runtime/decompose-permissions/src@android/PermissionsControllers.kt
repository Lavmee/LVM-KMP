package tech.annexflow.lvm.decompose.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.icerock.moko.permissions.PermissionsController

/**
 * Opens the system settings of the app's language, available since Android 13. On older versions, or when no screen
 * handles them, it opens the app's settings instead, as [PermissionsController.openAppSettings] does.
 */
public fun PermissionsController.openAppLocaleSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppSettings()
    }
}

/**
 * Opens the system settings of the app's notifications, available since Android 8.0. On older versions, or when no
 * screen handles them, it opens the app's settings instead, as [PermissionsController.openAppSettings] does.
 */
public fun PermissionsController.openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        openAppSettings()
        return
    }
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppSettings()
    }
}
