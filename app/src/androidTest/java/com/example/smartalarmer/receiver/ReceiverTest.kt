package com.example.smartalarmer.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        receiver.onReceive(context, intent)
    }
}
