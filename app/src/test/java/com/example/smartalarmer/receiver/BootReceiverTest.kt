package com.example.smartalarmer.receiver

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun wallClockAndLifecycleChangesRescheduleEnabledAlarms() {
        val actions =
            listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_USER_UNLOCKED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )

        actions.forEach { action ->
            assertTrue(BootReceiver.shouldReschedule(action, canScheduleExactAlarms = true))
        }
    }

    @Test
    fun exactAlarmPermissionEventReschedulesOnlyAfterPermissionIsGranted() {
        val action = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        assertTrue(BootReceiver.shouldReschedule(action, canScheduleExactAlarms = true))
        assertFalse(BootReceiver.shouldReschedule(action, canScheduleExactAlarms = false))
    }

    @Test
    fun unrelatedBroadcastIsIgnored() {
        assertFalse(BootReceiver.shouldReschedule("example.UNRELATED", canScheduleExactAlarms = true))
        assertFalse(BootReceiver.shouldReschedule(null, canScheduleExactAlarms = true))
    }

    @Test
    fun lockedClockChangesRecalculateButBootRecoveryPreservesMissedTrigger() {
        assertTrue(BootReceiver.shouldRecalculateLockedTrigger(Intent.ACTION_TIME_CHANGED))
        assertTrue(BootReceiver.shouldRecalculateLockedTrigger(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(BootReceiver.shouldRecalculateLockedTrigger(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertFalse(BootReceiver.shouldRecalculateLockedTrigger(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun lockedRestoreSkipsDisabledDeliveredOrStaleOneTimeAlarms() {
        val now = 100_000L
        val disabledAlarm = com.example.smartalarmer.data.Alarm(id = 1, hour = 8, minute = 0, daysOfWeek = "", isEnabled = false, puzzlesList = "MATH")
        val deliveredAlarm = com.example.smartalarmer.data.Alarm(id = 2, hour = 8, minute = 0, daysOfWeek = "", isEnabled = true, puzzlesList = "MATH")
        val staleOneTimeAlarm = com.example.smartalarmer.data.Alarm(id = 3, hour = 8, minute = 0, daysOfWeek = "", isEnabled = true, puzzlesList = "MATH")
        val validFutureOneTimeAlarm = com.example.smartalarmer.data.Alarm(id = 4, hour = 8, minute = 0, daysOfWeek = "", isEnabled = true, puzzlesList = "MATH")
        val validRecurringAlarm = com.example.smartalarmer.data.Alarm(id = 5, hour = 8, minute = 0, daysOfWeek = "1,2,3", isEnabled = true, puzzlesList = "MATH")

        assertTrue(
            BootReceiver.shouldSkipLockedRestore(
                com.example.smartalarmer.scheduler.DirectBootAlarmSnapshot(disabledAlarm, triggerAtMillis = 200_000L),
                deliveredIds = emptySet(),
                nowMillis = now
            )
        )

        assertTrue(
            BootReceiver.shouldSkipLockedRestore(
                com.example.smartalarmer.scheduler.DirectBootAlarmSnapshot(deliveredAlarm, triggerAtMillis = 200_000L),
                deliveredIds = setOf(2),
                nowMillis = now
            )
        )

        assertTrue(
            BootReceiver.shouldSkipLockedRestore(
                com.example.smartalarmer.scheduler.DirectBootAlarmSnapshot(staleOneTimeAlarm, triggerAtMillis = 90_000L),
                deliveredIds = emptySet(),
                nowMillis = now
            )
        )

        assertFalse(
            BootReceiver.shouldSkipLockedRestore(
                com.example.smartalarmer.scheduler.DirectBootAlarmSnapshot(validFutureOneTimeAlarm, triggerAtMillis = 200_000L),
                deliveredIds = emptySet(),
                nowMillis = now
            )
        )

        assertFalse(
            BootReceiver.shouldSkipLockedRestore(
                com.example.smartalarmer.scheduler.DirectBootAlarmSnapshot(validRecurringAlarm, triggerAtMillis = 90_000L),
                deliveredIds = emptySet(),
                nowMillis = now
            )
        )
    }
}
