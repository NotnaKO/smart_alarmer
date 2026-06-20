package com.example.smartalarmer.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.example.smartalarmer.AlarmItemCard
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDao
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.theme.SmartAlarmerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AlarmDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alarmDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun testAlarm(
        hour: Int = 7,
        minute: Int = 30,
        isEnabled: Boolean = true,
    ) = Alarm(
        id = 1,
        hour = hour,
        minute = minute,
        daysOfWeek = "1,2,3,4,5",
        isEnabled = isEnabled,
        puzzlesList = "MATH",
        puzzleCount = 1,
    )

    private fun setAlarmCard(
        alarm: Alarm,
        onToggle: (Boolean) -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SmartAlarmerTheme {
                AlarmItemCard(alarm = alarm, onToggle = onToggle, onDelete = onDelete)
            }
        }
    }

    // ── AlarmItemCard tests ───────────────────────────────────────────────

    @Test
    fun alarmCard_showsFormattedTime() {
        setAlarmCard(alarm = testAlarm(hour = 9, minute = 5))

        composeTestRule.onNodeWithText("09:05").assertIsDisplayed()
    }

    @Test
    fun alarmCard_showsTimeWithLeadingZeros() {
        setAlarmCard(alarm = testAlarm(hour = 0, minute = 0))

        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun alarmCard_enabledAlarm_switchIsChecked() {
        setAlarmCard(alarm = testAlarm(isEnabled = true))

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun alarmCard_disabledAlarm_switchIsUnchecked() {
        setAlarmCard(alarm = testAlarm(isEnabled = false))

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun alarmCard_toggleSwitch_callsOnToggle() {
        var toggledValue: Boolean? = null
        setAlarmCard(
            alarm = testAlarm(isEnabled = true),
            onToggle = { toggledValue = it },
        )

        composeTestRule.onNode(isToggleable()).performClick()

        assertNotNull("onToggle should have been called", toggledValue)
    }

    @Test
    fun alarmCard_deleteButton_callsOnDelete() {
        var deleted = false
        setAlarmCard(
            alarm = testAlarm(),
            onDelete = { deleted = true },
        )

        composeTestRule.onNodeWithContentDescription("Delete Alarm").performClick()

        assertTrue("onDelete should have been called", deleted)
    }

    @Test
    fun alarmCard_showsPuzzleLabel() {
        setAlarmCard(alarm = testAlarm())

        composeTestRule.onNodeWithText("Required puzzles: Math, Memory, Typing").assertIsDisplayed()
    }

    // ── Multiple alarms list ──────────────────────────────────────────────

    @Test
    fun multipleAlarmCards_allTimesVisible() {
        val alarms = listOf(
            testAlarm(hour = 6, minute = 0).copy(id = 1),
            testAlarm(hour = 7, minute = 15).copy(id = 2),
            testAlarm(hour = 8, minute = 45).copy(id = 3),
        )

        composeTestRule.setContent {
            SmartAlarmerTheme {
                Column {
                    alarms.forEach { alarm ->
                        AlarmItemCard(alarm = alarm, onToggle = {}, onDelete = {})
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("06:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("07:15").assertIsDisplayed()
        composeTestRule.onNodeWithText("08:45").assertIsDisplayed()
    }
}
