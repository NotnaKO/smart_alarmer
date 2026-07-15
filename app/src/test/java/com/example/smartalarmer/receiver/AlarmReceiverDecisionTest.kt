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
