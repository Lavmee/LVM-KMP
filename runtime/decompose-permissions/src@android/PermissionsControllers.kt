package tech.annexflow.lvm.decompose.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
