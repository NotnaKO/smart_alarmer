package com.example.smartalarmer.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmConfigurationTest {
    @Test
    fun alarmDays_normalizesLegacyCsv() {
        val days = AlarmDays.parse("7, 1,3,3,0,invalid,8")

        assertEquals(setOf(AlarmDay.MONDAY, AlarmDay.WEDNESDAY, AlarmDay.SUNDAY), days.values)
        assertEquals("1,3,7", days.encoded)
    }

    @Test
    fun alarmDays_invalidCsvRepresentsOneTimeAlarm() {
        assertEquals(AlarmDays.ONE_TIME, AlarmDays.parse("invalid,0,8"))
    }

    @Test
    fun alarmWeekParity_parsesKnownValuesAndFallsBackToEveryWeek() {
        assertEquals(AlarmWeekParity.ODD, AlarmWeekParity.parse(" odd "))
        assertEquals(AlarmWeekParity.EVEN, AlarmWeekParity.parse("EVEN"))
        assertEquals(AlarmWeekParity.EVERY, AlarmWeekParity.parse("unexpected"))
        assertEquals(AlarmWeekParity.EVERY, AlarmWeekParity.parse(null))
    }

    @Test
    fun puzzleSelection_normalizesKnownLegacyValues() {
        val selection = PuzzleSelection.parse("shake, MATH,shake,unknown")

        assertEquals(setOf(PuzzleType.MATH, PuzzleType.SHAKE), selection.values)
        assertEquals("MATH,SHAKE", selection.encoded)
    }

    @Test
    fun puzzleSelection_invalidCsvFallsBackToSolvableMath() {
        assertEquals(PuzzleSelection.DEFAULT, PuzzleSelection.parse("unknown,invalid"))
    }

    @Test
    fun puzzleSelection_parsingIsIndependentOfDefaultLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals(
                setOf(PuzzleType.TYPING),
                PuzzleSelection.parse("typing").values
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun alarmDraftPersistsWakeUpCheckConfiguration() {
        val alarm =
            AlarmDraft(
                hour = 7,
                minute = 30,
                repeatDays = AlarmDays.ONE_TIME,
                puzzleSelection = PuzzleSelection.DEFAULT,
                puzzleCount = 1,
                label = "Morning",
                soundUri = null,
                wakeUpChecksEnabled = true,
                wakeUpCheckCount = 4,
                wakeUpCheckIntervalMinutes = 10
            ).toAlarm(isEnabled = true)

        assertEquals(true, alarm.wakeUpChecksEnabled)
        assertEquals(4, alarm.wakeUpCheckCount)
        assertEquals(10, alarm.wakeUpCheckIntervalMinutes)
    }

    @Test
    fun alarmDraftPersistsWeekParityOnlyForRecurringAlarms() {
        val recurring =
            AlarmDraft(
                hour = 7,
                minute = 30,
                repeatDays = AlarmDays.of(listOf(AlarmDay.MONDAY)),
                repeatWeekParity = AlarmWeekParity.ODD,
                puzzleSelection = PuzzleSelection.DEFAULT,
                puzzleCount = 1,
                label = "",
                soundUri = null
            ).toAlarm(isEnabled = true)
        val oneTime =
            AlarmDraft(
                hour = 7,
                minute = 30,
                repeatDays = AlarmDays.ONE_TIME,
                repeatWeekParity = AlarmWeekParity.ODD,
                puzzleSelection = PuzzleSelection.DEFAULT,
                puzzleCount = 1,
                label = "",
                soundUri = null
            ).toAlarm(isEnabled = true)

        assertEquals(AlarmWeekParity.ODD.name, recurring.weekParity)
        assertEquals(AlarmWeekParity.EVERY.name, oneTime.weekParity)
    }
}
