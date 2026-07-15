package com.example.smartalarmer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.ui.main.AlarmEditSheet
import com.example.smartalarmer.ui.main.AlarmItemCard
import com.example.smartalarmer.ui.theme.SmartAlarmerTheme
import com.example.smartalarmer.utils.AlarmTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun testAlarm(
        hour: Int = 7,
        minute: Int = 30,
        daysOfWeek: String = "1,2,3,4,5",
        isEnabled: Boolean = true,
        puzzlesList: String = "MATH",
        puzzleCount: Int = 1,
        isGradualVolume: Boolean = false,
        label: String = "",
        soundUri: String? = null
    ) = Alarm(
        id = 1,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek,
        isEnabled = isEnabled,
        puzzlesList = puzzlesList,
        puzzleCount = puzzleCount,
        isGradualVolume = isGradualVolume,
        label = label,
        soundUri = soundUri
    )

    private fun setAlarmCard(
        alarm: Alarm,
        onToggle: (Boolean) -> Unit = {},
        onDelete: () -> Unit = {},
        onEdit: () -> Unit = {},
        onTest: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SmartAlarmerTheme {
                AlarmItemCard(
                    alarm = alarm,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onEdit = onEdit,
                    onTest = onTest
                )
            }
        }
    }

    private fun formattedTime(
        hour: Int,
        minute: Int
    ): String {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        return AlarmTimeFormatter.formatTime(context, hour, minute)
    }

    // ── AlarmItemCard tests ───────────────────────────────────────────────

    @Test
    fun alarmCard_showsFormattedTime() {
        setAlarmCard(alarm = testAlarm(hour = 9, minute = 5))

        composeTestRule.onNodeWithText(formattedTime(9, 5)).assertIsDisplayed()
    }

    @Test
    fun alarmCard_showsTimeWithLeadingZeros() {
        setAlarmCard(alarm = testAlarm(hour = 0, minute = 0))

        composeTestRule.onNodeWithText(formattedTime(0, 0)).assertIsDisplayed()
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
            onToggle = { toggledValue = it }
        )

        composeTestRule.onNode(isToggleable()).performClick()

        assertNotNull("onToggle should have been called", toggledValue)
    }

    @Test
    fun alarmCard_deleteButton_callsOnDelete() {
        var deleted = false
        setAlarmCard(
            alarm = testAlarm(),
            onDelete = { deleted = true }
        )

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val deleteDesc = context.getString(com.example.smartalarmer.R.string.delete_alarm_desc)
        composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()

        assertTrue("onDelete should have been called", deleted)
    }

    @Test
    fun alarmCard_displaysCustomDaysAndPuzzles() {
        setAlarmCard(
            alarm = testAlarm(daysOfWeek = "1,3,5", puzzlesList = "MATH,MEMORY", puzzleCount = 2)
        )

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val mon = context.getString(com.example.smartalarmer.R.string.day_mon)
        val wed = context.getString(com.example.smartalarmer.R.string.day_wed)
        val fri = context.getString(com.example.smartalarmer.R.string.day_fri)
        val math = context.getString(com.example.smartalarmer.R.string.puzzle_math)
        val memory = context.getString(com.example.smartalarmer.R.string.puzzle_memory)
        val defaultSound = context.getString(com.example.smartalarmer.R.string.sound_default)

        val expected = "$mon, $wed, $fri • $math, $memory (2 puzzles) • $defaultSound"
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun alarmCard_displaysOneTimeAlarm() {
        setAlarmCard(
            alarm = testAlarm(daysOfWeek = "", puzzlesList = "MATH")
        )

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val oneTime = context.getString(com.example.smartalarmer.R.string.one_time)
        val math = context.getString(com.example.smartalarmer.R.string.puzzle_math)
        val defaultSound = context.getString(com.example.smartalarmer.R.string.sound_default)

        val expected = "$oneTime • $math (1 puzzle) • $defaultSound"
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun alarmCard_hidesLegacyGradualVolumeSetting() {
        setAlarmCard(
            alarm = testAlarm(isGradualVolume = false)
        )

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val weekdays = context.getString(com.example.smartalarmer.R.string.weekdays)
        val math = context.getString(com.example.smartalarmer.R.string.puzzle_math)
        val defaultSound = context.getString(com.example.smartalarmer.R.string.sound_default)

        val expected = "$weekdays • $math (1 puzzle) • $defaultSound"
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun alarmCard_testButtonClick_triggersCallback() {
        var testClicked = false
        setAlarmCard(
            alarm = testAlarm(),
            onTest = { testClicked = true }
        )

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val testBtnDesc = context.getString(com.example.smartalarmer.R.string.test_btn)
        composeTestRule.onNodeWithContentDescription(testBtnDesc).performClick()
        assertTrue(testClicked)
    }

    @Test
    fun alarmCard_cardClick_triggersEditCallback() {
        var editClicked = false
        setAlarmCard(
            alarm = testAlarm(),
            onEdit = { editClicked = true }
        )

        // Click the card (finding the time text in the unmerged tree to perform click on the left side)
        composeTestRule.onNodeWithText(formattedTime(7, 30), useUnmergedTree = true).performClick()
        assertTrue(editClicked)
    }

    @Test
    fun alarmCard_withLabel_showsLabelAsTitleAndTimeAsSubtitle() {
        setAlarmCard(alarm = testAlarm(hour = 7, minute = 30, label = "Morning Gym"))

        composeTestRule.onNodeWithText("Morning Gym").assertIsDisplayed()
        composeTestRule.onNodeWithText(formattedTime(7, 30)).assertIsDisplayed()
    }

    @Test
    fun alarmCard_withoutLabel_showsTimeAsTitleOnly() {
        setAlarmCard(alarm = testAlarm(hour = 7, minute = 30, label = ""))

        composeTestRule.onNodeWithText(formattedTime(7, 30)).assertIsDisplayed()
        // No text matching Morning Gym should exist
        composeTestRule.onNodeWithText("Morning Gym").assertDoesNotExist()
    }

    // ── Multiple alarms list ──────────────────────────────────────────────

    @Test
    fun multipleAlarmCards_allTimesVisible() {
        val alarms =
            listOf(
                testAlarm(hour = 6, minute = 0).copy(id = 1),
                testAlarm(hour = 7, minute = 15).copy(id = 2),
                testAlarm(hour = 8, minute = 45).copy(id = 3)
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

        composeTestRule.onNodeWithText(formattedTime(6, 0)).assertIsDisplayed()
        composeTestRule.onNodeWithText(formattedTime(7, 15)).assertIsDisplayed()
        composeTestRule.onNodeWithText(formattedTime(8, 45)).assertIsDisplayed()
    }

    @Test
    fun alarmEditSheet_withoutShakeSensor_hidesAndSanitizesShakePuzzle() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        var savedPuzzles: String? = null
        var savedPuzzleCount: Int? = null

        composeTestRule.setContent {
            SmartAlarmerTheme {
                AlarmEditSheet(
                    alarm = testAlarm(puzzlesList = "SHAKE", puzzleCount = 1),
                    onDismiss = {},
                    onSave = { draft ->
                        savedPuzzles = draft.puzzleSelection.encoded
                        savedPuzzleCount = draft.puzzleCount
                    },
                    onPickSound = {},
                    selectedSoundName = context.getString(com.example.smartalarmer.R.string.sound_default),
                    initialLabel = "",
                    pickedSoundUri = null,
                    shakeSensorAvailable = false
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(com.example.smartalarmer.R.string.puzzle_shake))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(context.getString(com.example.smartalarmer.R.string.save))
            .performScrollTo()
            .performClick()

        assertEquals("MATH", savedPuzzles)
        assertEquals(1, savedPuzzleCount)
    }

    @Test
    fun alarmEditSheet_dayAndStepperControlsMeetTouchTargetMinimum() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        composeTestRule.setContent {
            SmartAlarmerTheme {
                AlarmEditSheet(
                    alarm = null,
                    onDismiss = {},
                    onSave = {},
                    onPickSound = {},
                    selectedSoundName = context.getString(com.example.smartalarmer.R.string.sound_default),
                    initialLabel = "",
                    pickedSoundUri = null,
                    shakeSensorAvailable = false
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(com.example.smartalarmer.R.string.day_mon))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeTestRule
            .onNodeWithContentDescription(context.getString(com.example.smartalarmer.R.string.increase_puzzle_count))
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }
}
