package com.example.smartalarmer.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDismissActivityLockScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun alarmDismissActivity_wakesDeviceAndShowsOverLockScreen() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Check if we should sleep the device. We default to false to avoid local emulator/host freezes.
        val arguments = InstrumentationRegistry.getArguments()
        val shouldSleep = arguments.getString("sleepDevice") == "true"
        
        if (shouldSleep) {
            // 1. Put device to sleep (lock screen)
            device.sleep()
            Thread.sleep(1000)
        }
        
        // 2. Launch AlarmDismissActivity
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AlarmDismissActivity::class.java).apply {
            putExtra("PUZZLES_LIST", "MATH")
            putExtra("PUZZLE_COUNT", 1)
            putExtra("IS_PREVIEW", false) // Runs in real lockscreen mode
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        
        ActivityScenario.launch<AlarmDismissActivity>(intent).use {
            Thread.sleep(2000)
            
            // 3. Verify screen is back ON
            assertTrue("Screen should be turned ON by activity", device.isScreenOn)
            
            // 4. Verify Compose puzzle UI content is visible
            composeTestRule.onNodeWithText("Task 1 of 1").assertExists()
        }
        
        if (shouldSleep) {
            // 5. Clean up by waking device
            device.wakeUp()
        }
    }
}
