package com.example.smartalarmer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.service.AlarmNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryEndToEndTest {
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun scheduledPreviewAlarmReachesReceiverAndServiceWithoutChangingVolume() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assumeTrue("Exact alarm access is required for this device test", alarmManager.canScheduleExactAlarms())
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val audioManager = context.getSystemService(AudioManager::class.java)
        notificationManager.deleteNotificationChannel(AlarmNotification.CHANNEL_ID)
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val operation =
            PendingIntent.getBroadcast(
                context,
                991_337,
                AlarmIntentContract.write(
                    Intent(context, AlarmReceiver::class.java),
                    AlarmLaunchPayload(isPreview = true)
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 1_500, null),
                operation
            )
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (
                SystemClock.elapsedRealtime() < deadline &&
                notificationManager.getNotificationChannel(AlarmNotification.CHANNEL_ID) == null
            ) {
                SystemClock.sleep(100)
            }

            assertNotNull(
                "Alarm service should create its notification channel within 30 seconds",
                notificationManager.getNotificationChannel(AlarmNotification.CHANNEL_ID)
            )
            assertEquals(volumeBefore, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
        } finally {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }
}
