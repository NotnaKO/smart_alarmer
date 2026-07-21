package com.example.smartalarmer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.smartalarmer.R
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType

internal data class AlarmForegroundNotification(
    val id: Int,
    val notification: Notification,
    val dismissPendingIntent: PendingIntent
)

internal class AlarmForegroundNotificationFactory(
    private val context: Context
) {
    fun create(payload: AlarmLaunchPayload): AlarmForegroundNotification {
        AlarmNotification.ensureChannel(context)
        val dismissPendingIntent = AlarmNotification.dismissPendingIntent(context, payload)
        val builder =
            NotificationCompat
                .Builder(context, AlarmNotification.CHANNEL_ID)
                .setContentTitle(
                    context.getString(
                        if (payload.launchType == AlarmLaunchType.WAKE_UP_CHECK) {
                            R.string.wake_up_check_title
                        } else {
                            R.string.wake_up_title
                        }
                    )
                )
                .setContentText(payload.alarmLabel.ifBlank { context.getString(R.string.wake_up_desc) })
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(dismissPendingIntent)
                .setOngoing(!payload.isPreview)
        if (!payload.isPreview) {
            builder.setFullScreenIntent(dismissPendingIntent, true)
        }
        return AlarmForegroundNotification(
            id = AlarmNotification.notificationIdForAlarm(payload.alarmId, payload.isPreview),
            notification = builder.build(),
            dismissPendingIntent = dismissPendingIntent
        )
    }
}
