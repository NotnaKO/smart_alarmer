package com.example.smartalarmer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.DeliveryTestScheduler
import com.example.smartalarmer.service.AlarmNotification
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryEndToEndTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

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

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun scheduledDeliveryTestUsesRealPathWithoutChangingSystemAlarmVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
            )
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assumeTrue("Exact alarm access is required for this device test", alarmManager.canScheduleExactAlarms())
        }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val volumeBefore = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val selectedSoundUri =
            requireNotNull(
                RingtoneManager.getActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_ALARM
                )
            ) {
                "The emulator must provide an alarm ringtone for this delivery test"
            }.toString()
        val deliveryTestNotificationId =
            AlarmNotification.notificationIdForPayload(
                AlarmLaunchPayload(launchType = AlarmLaunchType.DELIVERY_TEST)
            )
        val triggerAtMillis = System.currentTimeMillis() + 1_500L
        val alarm =
            Alarm(
                id = 77,
                hour = 7,
                minute = 30,
                daysOfWeek = "",
                puzzlesList = "MATH",
                puzzleCount = 1,
                label = "Delivery test",
                soundUri = selectedSoundUri
            )

        try {
            assertTrue(
                DeliveryTestScheduler.schedule(context, alarm, triggerAtMillis) is
                    AlarmScheduleResult.Scheduled
            )
            val deadline = SystemClock.elapsedRealtime() + 30_000L
            var activity: AlarmDismissActivity? = null
            var notificationObserved = false
            while (SystemClock.elapsedRealtime() < deadline && activity == null) {
                notificationObserved =
                    notificationObserved ||
                    notificationManager.activeNotifications.any {
                        it.id == deliveryTestNotificationId
                    }
                instrumentation.runOnMainSync {
                    activity =
                        ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<AlarmDismissActivity>()
                            .firstOrNull()
                }
                if (activity == null) SystemClock.sleep(100)
            }

            assertNotNull("Delivery test should open the alarm screen", activity)
            assertEquals(
                AlarmLaunchType.DELIVERY_TEST,
                AlarmIntentContract.read(requireNotNull(activity).intent).launchType
            )
            assertEquals(
                "MATH",
                AlarmIntentContract.read(requireNotNull(activity).intent).puzzlesList
            )
            assertEquals(
                "Delivery test should carry the selected ringtone into playback",
                selectedSoundUri,
                AlarmIntentContract.read(requireNotNull(activity).intent).soundUri
            )
            composeTestRule.waitUntil(
                timeoutMillis = 10_000L,
                conditionDescription = "configured delivery-test task preview"
            ) {
                composeTestRule
                    .onAllNodesWithText(
                        context.getString(R.string.task_progress_format, 1, 1)
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertEquals(volumeBefore, audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
            assertTrue(
                "Delivery-test notification should be posted while its foreground service is active",
                notificationObserved ||
                    notificationManager.activeNotifications.any {
                        it.id == deliveryTestNotificationId
                    }
            )
        } finally {
            DeliveryTestScheduler.cancel(context)
            context.stopService(Intent(context, AlarmService::class.java))
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AlarmDismissActivity>()
                    .forEach { it.finish() }
            }
        }
    }
}
