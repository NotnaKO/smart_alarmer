package com.example.smartalarmer.domain

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
    fun puzzleSelection_normalizesKnownLegacyValues() {
        val selection = PuzzleSelection.parse("shake, MATH,shake,unknown")

        assertEquals(setOf(PuzzleType.MATH, PuzzleType.SHAKE), selection.values)
        assertEquals("MATH,SHAKE", selection.encoded)
    }

    @Test
    fun puzzleSelection_invalidCsvFallsBackToSolvableMath() {
        assertEquals(PuzzleSelection.DEFAULT, PuzzleSelection.parse("unknown,invalid"))
    }
}
