package com.example.smartalarmer.service

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.smartalarmer.R
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity

object AlarmNotification {
    const val CHANNEL_ID = "AlarmChannel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.active_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    fun dismissPendingIntent(
        context: Context,
        payload: AlarmLaunchPayload
    ): PendingIntent {
        val dismissIntent =
            AlarmIntentContract
                .write(
                    Intent(context, AlarmDismissActivity::class.java),
                    payload
                ).apply {
                    data =
                        Uri
                            .Builder()
                            .scheme("smartalarmer")
                            .authority("dismiss")
                            .appendPath(
                                when {
                                    payload.isPreview -> "preview"
                                    payload.launchType == com.example.smartalarmer.alarm.AlarmLaunchType.WAKE_UP_CHECK ->
                                        "wake-up-check-${payload.alarmId}-${payload.wakeUpCheckNumber}"
                                    else -> payload.alarmId.toString()
                                }
                            )
                            .build()
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
        val options = creatorActivityOptions()
        return PendingIntent.getActivity(
            context,
            notificationIdForAlarm(payload.alarmId, payload.isPreview),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options?.toBundle()
        )
    }

    internal fun creatorBackgroundStartMode(sdkInt: Int): Int? = when {
        sdkInt >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        else -> null
    }

    private fun creatorActivityOptions(): ActivityOptions? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val mode = requireNotNull(creatorBackgroundStartMode(Build.VERSION.SDK_INT))
        return ActivityOptions.makeBasic().apply {
            pendingIntentCreatorBackgroundActivityStartMode = mode
        }
    }

    internal fun notificationIdForAlarm(
        alarmId: Int,
        isPreview: Boolean = false
    ): Int {
        if (isPreview) return PREVIEW_NOTIFICATION_ID
        return ALARM_NOTIFICATION_ID_BASE + Math.floorMod(alarmId, ALARM_NOTIFICATION_ID_RANGE)
    }

    private const val PREVIEW_NOTIFICATION_ID = 1
    private const val ALARM_NOTIFICATION_ID_BASE = 10_000
    private const val ALARM_NOTIFICATION_ID_RANGE = 1_000_000
}
