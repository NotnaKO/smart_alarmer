package com.example.smartalarmer.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverTest {

    @Test
    fun alarmReceiver_onReceiveALARM_TRIGGER_runsWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = AlarmReceiver()
        val intent = Intent("com.example.smartalarmer.ALARM_TRIGGER").apply {
            putExtra("PUZZLES_LIST", "MATH")
            putExtra("PUZZLE_COUNT", 1)
            putExtra("IS_PREVIEW", true)
        }

        try {
            receiver.onReceive(context, intent)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                assertFalse(
                    "Receiver preview mode must not launch AlarmDismissActivity",
                    resumedActivities.any { it is AlarmDismissActivity }
                )
            }
        } finally {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
