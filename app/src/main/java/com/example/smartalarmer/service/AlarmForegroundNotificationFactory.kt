package com.example.smartalarmer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.smartalarmer.R
import com.example.smartalarmer.alarm.AlarmExecutionMode
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.alarm.executionMode

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
                        } else if (payload.launchType == AlarmLaunchType.DELIVERY_TEST) {
                            R.string.delivery_test_notification_title
                        } else {
                            R.string.wake_up_title
                        }
                    )
                )
                .setContentText(
                    if (payload.launchType == AlarmLaunchType.DELIVERY_TEST) {
                        context.getString(R.string.delivery_test_notification_text)
                    } else {
                        payload.alarmLabel.ifBlank { context.getString(R.string.wake_up_desc) }
                    }
                )
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(dismissPendingIntent)
                .setOngoing(payload.executionMode == AlarmExecutionMode.REAL)
        if (payload.executionMode != AlarmExecutionMode.PREVIEW) {
            builder.setFullScreenIntent(dismissPendingIntent, true)
        }
        return AlarmForegroundNotification(
            id = AlarmNotification.notificationIdForPayload(payload),
            notification = builder.build(),
            dismissPendingIntent = dismissPendingIntent
        )
    }
}
