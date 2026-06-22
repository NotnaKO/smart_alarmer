package com.example.smartalarmer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.lang.reflect.Method

object DeviceUtils {

    fun isXiaomi(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        return manufacturer.equals("Xiaomi", ignoreCase = true) || 
               brand.equals("Xiaomi", ignoreCase = true) || 
               isMiUi()
    }

    fun isMiUi(): Boolean {
        val name = getSystemProperty("ro.miui.ui.version.name")
        val code = getSystemProperty("ro.miui.ui.version.code")
        return !name.isNullOrBlank() || !code.isNullOrBlank()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun getMiuiPermissionIntent(context: Context): Intent {
        if (!isXiaomi()) {
            return getStandardAppInfoIntent(context)
        }
        return try {
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        } catch (e: Exception) {
            getStandardAppInfoIntent(context)
        }
    }

    fun getBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun getStandardAppInfoIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun getSystemProperty(propName: String): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod: Method = systemProperties.getMethod("get", String::class.java)
            val value = getMethod.invoke(systemProperties, propName) as? String
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            null
        }
    }
}
