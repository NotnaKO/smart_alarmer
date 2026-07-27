package com.example.smartalarmer.receiver

import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReceiverDecisionTest {
    @Test
    fun onlyExistingEnabledAlarmsAreDelivered() {
        assertEquals(false, AlarmReceiver.shouldDeliver(null, 123L))
        assertEquals(false, AlarmReceiver.shouldDeliver(alarm(false, ""), 123L))
        assertEquals(
            true,
            AlarmReceiver.shouldDeliver(
                alarm(true, "").copy(scheduledTriggerAtMillis = 123L),
                123L
            )
        )
    }

    @Test
    fun staleMainOccurrenceIsRejectedAfterReschedule() {
        val alarm = alarm(true, "").copy(scheduledTriggerAtMillis = 456L)

        assertEquals(false, AlarmReceiver.shouldDeliver(alarm, 123L))
        assertEquals(true, AlarmReceiver.shouldDeliver(alarm, 456L))
    }

    @Test
    fun legacyMainOccurrenceWithoutIdentityRemainsDeliverable() {
        assertEquals(
            true,
            AlarmReceiver.shouldDeliver(
                alarm(true, ""),
                AlarmLaunchPayload.NO_OCCURRENCE
            )
        )
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
                AlarmLaunchPayload(
                    alarmId = currentAlarm.id,
                    puzzleCount = 1,
                    occurrenceTriggerAtMillis = 123_456L
                )
            )

        assertEquals(AlarmLaunchType.MAIN, delivered.launchType)
        assertEquals(2, delivered.puzzleCount)
        assertEquals(123_456L, delivered.occurrenceTriggerAtMillis)
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
