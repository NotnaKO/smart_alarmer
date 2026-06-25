package com.example.smartalarmer.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun alarmService_createsNotificationChannelOnStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AlarmService::class.java).apply {
            putExtra("PUZZLES_LIST", "MATH")
            putExtra("PUZZLE_COUNT", 1)
            putExtra("IS_PREVIEW", true)
        }

        serviceRule.startService(intent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel("AlarmChannel")
            assertNotNull("AlarmChannel should be created", channel)
            assertEquals("Active Alarm", channel?.name)
        }
    }

    @Test
    fun alarmService_calculateGradualVolume_progressionCorrect() {
        val maxVolume = 10
        val volStart = AlarmService.calculateGradualVolume(0, maxVolume, 60L)
        val volMid = AlarmService.calculateGradualVolume(30, maxVolume, 60L)
        val volEnd = AlarmService.calculateGradualVolume(60, maxVolume, 60L)
        val volPast = AlarmService.calculateGradualVolume(90, maxVolume, 60L)

        assertEquals(1, volStart)
        assertEquals(5, volMid)
        assertEquals(maxVolume, volEnd)
        assertEquals(maxVolume, volPast)
    }
}
