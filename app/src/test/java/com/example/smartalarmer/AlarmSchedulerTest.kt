package com.example.smartalarmer

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {
    @Test
    fun scheduleExact_permissionUnavailable_doesNotSchedule() {
        var actionCalled = false

        val result = AlarmScheduler.scheduleExact(
            triggerAtMillis = 1234L,
            canScheduleExactAlarm = false
        ) {
            actionCalled = true
        }

        assertEquals(
            AlarmScheduleResult.PermissionRequired,
            result
        )
        assertFalse(actionCalled)
    }

    @Test
    fun scheduleExact_success_returnsTriggerTime() {
        var actionCalled = false

        val result = AlarmScheduler.scheduleExact(
            triggerAtMillis = 5678L,
            canScheduleExactAlarm = true
        ) {
            actionCalled = true
        }

        assertTrue(actionCalled)
        assertEquals(
            AlarmScheduleResult.Scheduled(5678L),
            result
        )
    }

    @Test
    fun scheduleExact_securityException_requestsPermission() {
        val result = AlarmScheduler.scheduleExact(
            triggerAtMillis = 1234L,
            canScheduleExactAlarm = true
        ) {
            throw SecurityException("permission revoked")
        }

        assertEquals(
            AlarmScheduleResult.PermissionRequired,
            result
        )
    }

    @Test
    fun scheduleExact_unexpectedFailure_isReturned() {
        val exception = IllegalStateException("alarm manager unavailable")

        val result = AlarmScheduler.scheduleExact(
            triggerAtMillis = 1234L,
            canScheduleExactAlarm = true
        ) {
            throw exception
        }

        assertTrue(result is AlarmScheduleResult.Failure)
        assertSame(
            exception,
            (result as AlarmScheduleResult.Failure).exception
        )
    }
}
