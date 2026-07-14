package com.example.smartalarmer.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DeviceUtils {
    fun isXiaomi(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val isXiaomiBrand =
            manufacturer.equals("Xiaomi", ignoreCase = true) ||
                brand.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) ||
                brand.equals("Redmi", ignoreCase = true) ||
                manufacturer.equals("POCO", ignoreCase = true) ||
                brand.equals("POCO", ignoreCase = true) ||
                manufacturer.equals("POCOPHONE", ignoreCase = true) ||
                brand.equals("POCOPHONE", ignoreCase = true)
        return isXiaomiBrand
    }

    fun getMiuiPermissionIntent(context: Context): Intent {
        if (!isXiaomi()) {
            return getStandardAppInfoIntent(context)
        }
        return Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        }
    }

    fun getStandardAppInfoIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}
