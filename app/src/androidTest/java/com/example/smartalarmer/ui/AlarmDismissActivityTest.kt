package com.example.smartalarmer.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDismissActivityTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @org.junit.Before
    fun setUp() {
        // Ensure emulator screen is awake and unlocked
        val device =
            androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation()
            )
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
        } catch (e: java.lang.Exception) {
            // Safe fallback if UiDevice is not available
        }
    }

    @Test
    fun alarmDismissActivity_inPreviewMode_finishesOnBackPress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, AlarmDismissActivity::class.java).apply {
                putExtra("PUZZLES_LIST", "MATH")
                putExtra("PUZZLE_COUNT", 1)
                putExtra("IS_PREVIEW", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        ActivityScenario.launch<AlarmDismissActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue("Activity should finish in preview mode on back press", activity.isFinishing)
            }
        }
    }

    @Test
    fun alarmDismissActivity_inRealMode_trapsBackPress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, AlarmDismissActivity::class.java).apply {
                putExtra("PUZZLES_LIST", "MATH")
                putExtra("PUZZLE_COUNT", 1)
                putExtra("IS_PREVIEW", false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        ActivityScenario.launch<AlarmDismissActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue("Activity should NOT finish in real mode on back press", !activity.isFinishing)
            }
        }
    }

    @Test
    fun alarmDismissActivity_newAlarmIntent_replacesDisplayedSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstIntent =
            Intent(context, AlarmDismissActivity::class.java).apply {
                putExtra("ALARM_ID", 1)
                putExtra("PUZZLES_LIST", "MATH")
                putExtra("PUZZLE_COUNT", 1)
                putExtra("ALARM_LABEL", "First alarm")
                putExtra("IS_PREVIEW", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        ActivityScenario.launch<AlarmDismissActivity>(firstIntent).use { scenario ->
            val secondIntent =
                Intent(context, AlarmDismissActivity::class.java).apply {
                    putExtra("ALARM_ID", 2)
                    putExtra("PUZZLES_LIST", "MATH")
                    putExtra("PUZZLE_COUNT", 1)
                    putExtra("ALARM_LABEL", "Second alarm")
                    putExtra("IS_PREVIEW", true)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            context.startActivity(secondIntent)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Second alarm").fetchSemanticsNodes().isNotEmpty()
            }
            scenario.onActivity { activity ->
                assertEquals(2, activity.intent.getIntExtra("ALARM_ID", -1))
                assertEquals("Second alarm", activity.intent.getStringExtra("ALARM_LABEL"))
            }
            composeTestRule.onNodeWithText("Second alarm").assertIsDisplayed()
        }
    }
}
