package com.example.smartalarmer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        
        // Ensure emulator screen is awake and unlocked
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        )
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
        } catch (e: Exception) {
            // Safe fallback if UiDevice is not available
        }

        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AlarmDatabase.getDatabase(context).clearAllTables()
        }
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            AlarmDatabase.getDatabase(context).clearAllTables()
        }
    }

    @Test
    fun mainActivity_showsNoAlarmsScheduledInitially() {
        val context = composeTestRule.activity
        val noAlarmsText = context.getString(com.example.smartalarmer.R.string.no_alarms_scheduled)
        composeTestRule.onNodeWithText(noAlarmsText).assertIsDisplayed()
    }

    @Test
    fun mainActivity_clickAddButton_showsBottomSheet() {
        val context = composeTestRule.activity
        val addBtnDesc = context.getString(com.example.smartalarmer.R.string.add_alarm_desc)
        composeTestRule.onNodeWithContentDescription(addBtnDesc).performClick()

        val newAlarmText = context.getString(com.example.smartalarmer.R.string.new_alarm)
        composeTestRule.onNodeWithText(newAlarmText).assertIsDisplayed()
    }

    @Test
    fun mainActivity_addAlarm_savesAlarmToList() {
        val context = composeTestRule.activity
        val addBtnDesc = context.getString(com.example.smartalarmer.R.string.add_alarm_desc)
        composeTestRule.onNodeWithContentDescription(addBtnDesc).performClick()

        val labelPlaceholder = context.getString(com.example.smartalarmer.R.string.label_placeholder)
        composeTestRule.onNodeWithText(labelPlaceholder).performTextReplacement("Plan Test Alarm")

        // Select Monday
        val dayMon = context.getString(com.example.smartalarmer.R.string.day_m)
        composeTestRule.onNodeWithText(dayMon).performClick()

        // Click Save
        val saveBtn = context.getString(com.example.smartalarmer.R.string.save)
        composeTestRule.onNodeWithText(saveBtn).performClick()

        // Wait and verify
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Plan Test Alarm").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Plan Test Alarm").assertIsDisplayed()
    }
}
