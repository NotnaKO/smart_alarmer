package com.example.smartalarmer.receiver

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun wallClockAndLifecycleChangesRescheduleEnabledAlarms() {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
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
}
