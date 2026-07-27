package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.WakeUpCheckSession
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmCardSortingTest {
    @Test
    fun enabledAlarmsAreOrderedByPersistedNextOccurrence() {
        val laterClockTimeButEarlierDay = alarm(id = 1, hour = 9, triggerAtMillis = 1_000L)
        val earlierClockTimeButLaterDay = alarm(id = 2, hour = 6, triggerAtMillis = 2_000L)

        val sorted =
            sortedAlarmCards(
                alarms = listOf(earlierClockTimeButLaterDay, laterClockTimeButEarlierDay),
                sessions = emptyList()
            )

        assertEquals(listOf(1, 2), sorted.map { it.alarm.id })
    }

    @Test
    fun skippedAlarmMovesToItsReplacementOccurrencePosition() {
        val skipped =
            alarm(id = 1, hour = 6, triggerAtMillis = 3_000L)
                .copy(suppressedThroughEpochDay = 25_000L)
        val tomorrow = alarm(id = 2, hour = 9, triggerAtMillis = 2_000L)

        val sorted = sortedAlarmCards(listOf(skipped, tomorrow), emptyList())

        assertEquals(listOf(2, 1), sorted.map { it.alarm.id })
    }

    @Test
    fun activeWakeUpCheckUsesEarlierCheckTriggerAndDisabledAlarmsStayLast() {
        val enabled = alarm(id = 1, hour = 7, triggerAtMillis = 5_000L)
        val activeCheckAlarm =
            alarm(id = 2, hour = 8, triggerAtMillis = null, enabled = false)
        val disabled = alarm(id = 3, hour = 6, triggerAtMillis = null, enabled = false)
        val session =
            WakeUpCheckSession(
                alarmId = 2,
                token = "token",
                nextCheckNumber = 1,
                totalChecks = 3,
                intervalMinutes = 5,
                nextTriggerAtMillis = 1_000L,
                puzzlesList = "MATH",
                soundUri = null,
                alarmLabel = ""
            )

        val sorted =
            sortedAlarmCards(
                alarms = listOf(disabled, enabled, activeCheckAlarm),
                sessions = listOf(session)
            )

        assertEquals(listOf(2, 1, 3), sorted.map { it.alarm.id })
    }

    private fun alarm(
        id: Int,
        hour: Int,
        triggerAtMillis: Long?,
        enabled: Boolean = true
    ) = Alarm(
        id = id,
        hour = hour,
        minute = 0,
        daysOfWeek = "1,2,3,4,5",
        isEnabled = enabled,
        puzzlesList = "MATH",
        scheduledTriggerAtMillis = triggerAtMillis
    )
}
