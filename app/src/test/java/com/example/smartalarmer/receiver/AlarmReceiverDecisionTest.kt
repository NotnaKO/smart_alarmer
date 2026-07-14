package com.example.smartalarmer.receiver

import com.example.smartalarmer.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReceiverDecisionTest {
    @Test
    fun onlyExistingEnabledAlarmsAreDelivered() {
        assertEquals(false, AlarmReceiver.shouldDeliver(null))
        assertEquals(false, AlarmReceiver.shouldDeliver(alarm(false, "")))
        assertEquals(true, AlarmReceiver.shouldDeliver(alarm(true, "")))
    }

    @Test
    fun disabledAlarmGetsNoFollowUp() {
        assertEquals(AlarmFollowUp.NONE, AlarmReceiver.followUpFor(alarm(false, "1")))
    }

    @Test
    fun enabledOneTimeAlarmIsDisabled() {
        assertEquals(AlarmFollowUp.DISABLE, AlarmReceiver.followUpFor(alarm(true, "")))
    }

    @Test
    fun enabledRecurringAlarmIsRescheduled() {
        assertEquals(AlarmFollowUp.RESCHEDULE, AlarmReceiver.followUpFor(alarm(true, "1,3")))
    }

    private fun alarm(
        enabled: Boolean,
        days: String
    ) = Alarm(
        id = 1,
        hour = 7,
        minute = 0,
        daysOfWeek = days,
        isEnabled = enabled,
        puzzlesList = "MATH"
    )
}
