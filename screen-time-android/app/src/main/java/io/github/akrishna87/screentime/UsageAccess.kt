package io.github.akrishna87.screentime

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/** The "Usage access" special permission, which the user turns on in Settings. */
object UsageAccess {
    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun openSettings(context: Context) {
        val list = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        // Newer phones can open this app's own switch; older ones show the list of apps.
        val direct = Intent(list).setData(Uri.fromParts("package", context.packageName, null))
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(list)
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } catch (e: ActivityNotFoundException) {
            // Uninstalled since; nothing to show.
        }
    }
}
