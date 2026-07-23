package com.example.smartalarmer.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartalarmer.R
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.ui.main.ALARM_CARD_ACTIONS_TAG
import com.example.smartalarmer.ui.main.ALARM_CARD_SUMMARY_TAG
import com.example.smartalarmer.ui.main.ALARM_CARD_TAG
import com.example.smartalarmer.ui.main.ALARM_EDITOR_DAYS_TAG
import com.example.smartalarmer.ui.main.ALARM_EDITOR_PUZZLE_COUNT_TAG
import com.example.smartalarmer.ui.main.ALARM_EDITOR_REPEAT_TAG
import com.example.smartalarmer.ui.main.ALARM_EDITOR_SOUND_ROW_TAG
import com.example.smartalarmer.ui.main.ALARM_EDITOR_WAKE_UP_CHECKS_TAG
import com.example.smartalarmer.ui.main.AlarmEditSheet
import com.example.smartalarmer.ui.main.AlarmItemCard
import com.example.smartalarmer.ui.main.MAIN_HEADER_PRIVACY_TAG
import com.example.smartalarmer.ui.main.MAIN_HEADER_TAG
import com.example.smartalarmer.ui.main.MAIN_HEADER_TITLE_TAG
import com.example.smartalarmer.ui.main.MainScreenHeader
import com.example.smartalarmer.ui.theme.SmartAlarmerTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var localizedContext: Context

    @Test
    fun dashboard_englishTextFitsCompactWidth() = assertDashboardFits("en")

    @Test
    fun dashboard_germanTextFitsCompactWidth() = assertDashboardFits("de")

    @Test
    fun dashboard_spanishTextFitsCompactWidth() = assertDashboardFits("es")

    @Test
    fun dashboard_russianTextFitsCompactWidth() = assertDashboardFits("ru")

    @Test
    fun alarmEditor_russianTextAndControlsFitCompactWidth() {
        setLocalizedContent("ru") {
            Box(modifier = Modifier.width(COMPACT_WIDTH).fillMaxHeight()) {
                AlarmEditSheet(
                    alarm = null,
                    onDismiss = {},
                    onSave = {},
                    onPickSound = {},
                    selectedSoundName = localizedContext.getString(R.string.select_alarm_sound),
                    initialLabel = "",
                    pickedSoundUri = null,
                    shakeSensorAvailable = true
                )
            }
        }

        val soundRow =
            composeTestRule
                .onNodeWithTag(ALARM_EDITOR_SOUND_ROW_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val soundLabel =
            composeTestRule
                .onNodeWithText(
                    localizedContext.getString(R.string.sound_label),
                    useUnmergedTree = true
                )
                .fetchSemanticsNode()
                .boundsInRoot
        val soundValue =
            composeTestRule
                .onNodeWithText(
                    localizedContext.getString(R.string.select_alarm_sound),
                    useUnmergedTree = true
                )
                .fetchSemanticsNode()
                .boundsInRoot
        assertWithin(soundRow, soundLabel, "Russian sound label")
        assertWithin(soundRow, soundValue, "Russian sound value")
        assertTrue("Sound label and value overlap", soundLabel.right <= soundValue.left + PIXEL_TOLERANCE)

        val puzzleCountText = localizedContext.getString(R.string.puzzles_required)
        composeTestRule.onNodeWithText(puzzleCountText).performScrollTo()
        val puzzleCountRow =
            composeTestRule.onNodeWithTag(ALARM_EDITOR_PUZZLE_COUNT_TAG).fetchSemanticsNode().boundsInRoot
        val puzzleCountLabel =
            composeTestRule.onNodeWithText(puzzleCountText).fetchSemanticsNode().boundsInRoot
        val decreaseButton =
            composeTestRule
                .onNodeWithContentDescription(localizedContext.getString(R.string.decrease_puzzle_count))
                .fetchSemanticsNode()
                .boundsInRoot
        assertWithin(puzzleCountRow, puzzleCountLabel, "Russian puzzle-count label")
        assertWithin(puzzleCountRow, decreaseButton, "Puzzle-count controls")
        assertTrue(
            "Puzzle-count label overlaps its controls",
            puzzleCountLabel.right <= decreaseButton.left + PIXEL_TOLERANCE
        )
        composeTestRule.onNodeWithTag(ALARM_EDITOR_WAKE_UP_CHECKS_TAG).performScrollTo()
        val visiblePuzzleCountRow =
            composeTestRule.onNodeWithTag(ALARM_EDITOR_PUZZLE_COUNT_TAG).fetchSemanticsNode().boundsInRoot
        val wakeUpChecks =
            composeTestRule.onNodeWithTag(ALARM_EDITOR_WAKE_UP_CHECKS_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Puzzle-count controls overlap the wake-up-check card",
            visiblePuzzleCountRow.bottom <= wakeUpChecks.top
        )

        composeTestRule
            .onNode(isToggleable() and hasAnyAncestor(hasTestTag(ALARM_EDITOR_REPEAT_TAG)))
            .performScrollTo()
            .performClick()
        val monday = localizedContext.getString(R.string.day_mon)
        composeTestRule.onNodeWithContentDescription(monday).performScrollTo()
        val days = composeTestRule.onNodeWithTag(ALARM_EDITOR_DAYS_TAG).fetchSemanticsNode().boundsInRoot
        val dayBounds =
            listOf(
                R.string.day_mon,
                R.string.day_tue,
                R.string.day_wed,
                R.string.day_thu,
                R.string.day_fri,
                R.string.day_sat,
                R.string.day_sun
            ).map { dayResource ->
                val day = localizedContext.getString(dayResource)
                composeTestRule
                    .onNodeWithContentDescription(day)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .also { assertWithin(days, it, "$day weekday target") }
            }
        assertTrue(
            "Russian weekday targets must stay on one row",
            dayBounds.all { bounds ->
                kotlin.math.abs(bounds.top - dayBounds.first().top) <= PIXEL_TOLERANCE
            }
        )

        listOf(
            localizedContext.getString(R.string.volume_ramp_seconds_format, 30),
            localizedContext.getString(R.string.volume_ramp_minutes_format, 1),
            localizedContext.getString(R.string.volume_ramp_minutes_format, 2),
            localizedContext.getString(R.string.volume_ramp_minutes_format, 4)
        ).forEach { duration ->
            composeTestRule.onNodeWithText(duration).assertExists()
        }
    }

    private fun assertDashboardFits(languageTag: String) {
        setLocalizedContent(languageTag) {
            Column(modifier = Modifier.width(COMPACT_WIDTH)) {
                MainScreenHeader(onPrivacyPolicyClick = {})
                AlarmItemCard(
                    alarm =
                    Alarm(
                        id = 1,
                        hour = 7,
                        minute = 30,
                        daysOfWeek = "1,2,3,4,5,6,7",
                        isEnabled = false,
                        puzzlesList = "MATH,TYPING,MEMORY,SHAKE",
                        puzzleCount = 4,
                        label = localizedContext.getString(R.string.app_name)
                    ),
                    onToggle = {},
                    onDelete = {}
                )
            }
        }

        val header =
            composeTestRule
                .onNodeWithTag(MAIN_HEADER_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val title =
            composeTestRule
                .onNodeWithTag(MAIN_HEADER_TITLE_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val privacy =
            composeTestRule
                .onNodeWithTag(MAIN_HEADER_PRIVACY_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertWithin(header, title, "$languageTag app title")
        assertWithin(header, privacy, "$languageTag privacy action")
        assertTrue("$languageTag header text overlaps", title.bottom <= privacy.top + PIXEL_TOLERANCE)

        val card =
            composeTestRule
                .onNodeWithTag(ALARM_CARD_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summary =
            composeTestRule
                .onNodeWithTag(ALARM_CARD_SUMMARY_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val actions =
            composeTestRule
                .onNodeWithTag(ALARM_CARD_ACTIONS_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertWithin(card, summary, "$languageTag alarm summary")
        assertWithin(card, actions, "$languageTag alarm actions")
        assertTrue("$languageTag alarm summary overlaps actions", summary.bottom <= actions.top + PIXEL_TOLERANCE)
    }

    private fun setLocalizedContent(
        languageTag: String,
        content: @Composable () -> Unit
    ) {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(baseContext.resources.configuration).apply { setLocale(locale) }
        localizedContext = baseContext.createConfigurationContext(configuration)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalResources provides localizedContext.resources
            ) {
                SmartAlarmerTheme { content() }
            }
        }
    }

    private fun assertWithin(
        parent: Rect,
        child: Rect,
        description: String
    ) {
        assertTrue("$description starts outside its container: $child vs $parent", child.left >= parent.left - PIXEL_TOLERANCE)
        assertTrue("$description ends outside its container: $child vs $parent", child.right <= parent.right + PIXEL_TOLERANCE)
        assertTrue("$description is above its container: $child vs $parent", child.top >= parent.top - PIXEL_TOLERANCE)
        assertTrue("$description is below its container: $child vs $parent", child.bottom <= parent.bottom + PIXEL_TOLERANCE)
    }

    private companion object {
        val COMPACT_WIDTH = 320.dp
        const val PIXEL_TOLERANCE = 1f
    }
}
