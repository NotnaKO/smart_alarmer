package com.example.smartalarmer.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smartalarmer.service.AlarmNotification

data class AlarmCapabilityState(
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val alarmChannelUsable: Boolean,
    val exactAlarmAccess: Boolean,
    val fullScreenIntentAccess: Boolean
) {
    val notificationDeliveryReady: Boolean
        get() = notificationPermissionGranted && notificationsEnabled && alarmChannelUsable
}

object AlarmCapabilityChecker {
    fun check(context: Context): AlarmCapabilityState {
        AlarmNotification.ensureChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val channelUsable =
            notificationManager
                .getNotificationChannel(AlarmNotification.CHANNEL_ID)
                ?.let { it.importance >= NotificationManager.IMPORTANCE_HIGH } == true
        val exactAlarmAccess =
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        val fullScreenIntentAccess =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent()
            } else {
                true
            }

        return AlarmCapabilityState(
            notificationPermissionGranted = notificationPermissionGranted,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            alarmChannelUsable = channelUsable,
            exactAlarmAccess = exactAlarmAccess,
            fullScreenIntentAccess = fullScreenIntentAccess
        )
    }

    internal fun requiresExactAlarmSpecialAccess(sdkInt: Int): Boolean = sdkInt in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2
}
