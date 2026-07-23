package com.example.smartalarmer

import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.service.AlarmOverlapDecision
import com.example.smartalarmer.service.AlarmService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmServiceTest {
    @Test
    fun testCalculateGradualVolume() {
        val maxVolume = 7

        // Start of playback (0s) -> minimum volume 1
        assertEquals(1, AlarmService.calculateGradualVolume(0L, maxVolume))

        // Quarter-way through (15s) -> 1 + 15 * 6 / 60 = 2
        assertEquals(2, AlarmService.calculateGradualVolume(15L, maxVolume))

        // Halfway through (30s) -> 1 + 30 * 6 / 60 = 4
        assertEquals(4, AlarmService.calculateGradualVolume(30L, maxVolume))

        // Three-quarter-way through (45s) -> 1 + 45 * 6 / 60 = 5
        assertEquals(5, AlarmService.calculateGradualVolume(45L, maxVolume))

        // End of crescendo (60s) -> max volume 7
        assertEquals(7, AlarmService.calculateGradualVolume(60L, maxVolume))

        // Beyond crescendo (75s) -> max volume 7
        assertEquals(7, AlarmService.calculateGradualVolume(75L, maxVolume))
    }

    @Test
    fun overlapPolicyStartsWithoutAnActiveAlarm() {
        assertEquals(
            AlarmOverlapDecision.START,
            AlarmService.overlapDecision(null, AlarmLaunchPayload(alarmId = 42))
        )
    }

    @Test
    fun overlapPolicyQueuesDistinctAlarmsWithoutReplacingProgress() {
        assertEquals(
            AlarmOverlapDecision.QUEUE,
            AlarmService.overlapDecision(
                AlarmLaunchPayload(alarmId = 41),
                AlarmLaunchPayload(alarmId = 42)
            )
        )
    }

    @Test
    fun overlapPolicyRecognizesRedeliveryButQueuesLaterWakeUpCheck() {
        val main = AlarmLaunchPayload(alarmId = 42)
        assertEquals(
            AlarmOverlapDecision.REDELIVERY,
            AlarmService.overlapDecision(main, main)
        )
        assertEquals(
            AlarmOverlapDecision.QUEUE,
            AlarmService.overlapDecision(
                main,
                AlarmLaunchPayload(
                    alarmId = 42,
                    launchType = AlarmLaunchType.WAKE_UP_CHECK,
                    wakeUpCheckNumber = 1,
                    wakeUpCheckToken = "token"
                )
            )
        )
    }

    @Test
    fun notificationIds_areStableAndUniquePerAlarm() {
        val first = AlarmService.notificationIdForAlarm(42)

        assertEquals(first, AlarmService.notificationIdForAlarm(42))
        assertNotEquals(first, AlarmService.notificationIdForAlarm(43))
        assertNotEquals(first, AlarmService.notificationIdForAlarm(42, isPreview = true))
    }

    @Test
    fun notificationIds_supportNegativeAndLargeAlarmIds() {
        val negative = AlarmService.notificationIdForAlarm(-1)
        val large = AlarmService.notificationIdForAlarm(Int.MAX_VALUE)

        assertTrue(negative >= 10_000)
        assertTrue(large >= 10_000)
        assertNotEquals(negative, large)
    }
}
