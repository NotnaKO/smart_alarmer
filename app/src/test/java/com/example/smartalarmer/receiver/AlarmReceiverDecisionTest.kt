package com.example.smartalarmer.receiver

import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
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
    fun wakeUpCheckDeliveryPreservesItsSessionIdentity() {
        val scheduledPayload =
            AlarmLaunchPayload(
                alarmId = 1,
                launchType = AlarmLaunchType.WAKE_UP_CHECK,
                wakeUpCheckNumber = 1,
                wakeUpCheckTotal = 1,
                wakeUpCheckToken = "session-token"
            )

        val delivered = AlarmReceiver.payloadForDelivery(alarm(true, ""), scheduledPayload)

        assertEquals(AlarmLaunchType.WAKE_UP_CHECK, delivered.launchType)
        assertEquals(1, delivered.wakeUpCheckNumber)
        assertEquals(1, delivered.wakeUpCheckTotal)
        assertEquals("session-token", delivered.wakeUpCheckToken)
    }

    @Test
    fun mainDeliveryRefreshesConfigurationFromAlarm() {
        val currentAlarm = alarm(true, "").copy(puzzleCount = 2, puzzlesList = "MATH,MEMORY")

        val delivered =
            AlarmReceiver.payloadForDelivery(
                currentAlarm,
                AlarmLaunchPayload(alarmId = currentAlarm.id, puzzleCount = 1)
            )

        assertEquals(AlarmLaunchType.MAIN, delivered.launchType)
        assertEquals(2, delivered.puzzleCount)
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
