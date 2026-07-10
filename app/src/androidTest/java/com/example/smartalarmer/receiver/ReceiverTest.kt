package com.example.smartalarmer.receiver

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.service.AlarmService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverTest {

    @Test
    fun alarmReceiver_previewTrigger_forwardsSafeServiceIntent() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        var capturedServiceIntent: Intent? = null
        val context = object : ContextWrapper(baseContext) {
            override fun startForegroundService(service: Intent): ComponentName? {
                capturedServiceIntent = service
                return service.component
            }

            override fun startService(service: Intent): ComponentName? {
                capturedServiceIntent = service
                return service.component
            }
        }
        val receiver = AlarmReceiver()
        val intent = Intent("com.example.smartalarmer.ALARM_TRIGGER").apply {
            putExtra("PUZZLES_LIST", "MATH")
            putExtra("PUZZLE_COUNT", 1)
            putExtra("IS_PREVIEW", true)
        }

        receiver.onReceive(context, intent)

        assertNotNull(capturedServiceIntent)
        assertEquals(AlarmService::class.java.name, capturedServiceIntent?.component?.className)
        assertTrue(capturedServiceIntent?.getBooleanExtra("IS_PREVIEW", false) == true)
    }
}
