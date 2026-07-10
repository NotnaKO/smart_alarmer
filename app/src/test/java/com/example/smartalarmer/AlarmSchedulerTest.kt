package com.example.smartalarmer

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {

    private fun getMockCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun testCalculateNextTriggerTime_OneTimeAlarm_FutureTime() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 10:30 on same day
        val alarm = Alarm(
            hour = 10,
            minute = 30,
            daysOfWeek = "", // One-time alarm
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        assertEquals(2026, nextTrigger.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, nextTrigger.get(Calendar.MONTH))
        assertEquals(18, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, nextTrigger.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextTrigger.get(Calendar.MINUTE))
    }

    @Test
    fun testCalculateNextTriggerTime_OneTimeAlarm_PastTime() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 09:30 (already past for today)
        val alarm = Alarm(
            hour = 9,
            minute = 30,
            daysOfWeek = "", // One-time alarm
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        // Should schedule for tomorrow Friday 2026-06-19 09:30:00
        assertEquals(2026, nextTrigger.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, nextTrigger.get(Calendar.MONTH))
        assertEquals(19, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextTrigger.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextTrigger.get(Calendar.MINUTE))
    }

    @Test
    fun testCalculateNextTriggerTime_RecurringAlarm_SameDayFuture() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 10:30, active days: Monday (1), Thursday (4)
        val alarm = Alarm(
            hour = 10,
            minute = 30,
            daysOfWeek = "1,4",
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        // Should schedule on same day (Thursday, 18th)
        assertEquals(18, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.THURSDAY, nextTrigger.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun testCalculateNextTriggerTime_RecurringAlarm_SameDayPast() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 09:30, active days: Monday (1), Thursday (4)
        val alarm = Alarm(
            hour = 9,
            minute = 30,
            daysOfWeek = "1,4",
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        // Should schedule on next active day: Monday 2026-06-22 (since Friday/Saturday/Sunday are not active)
        assertEquals(22, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, nextTrigger.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun testCalculateNextTriggerTime_RecurringAlarm_DifferentDayFuture() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 12:00, active days: Saturday (6), Sunday (7)
        val alarm = Alarm(
            hour = 12,
            minute = 0,
            daysOfWeek = "6,7",
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        // Should schedule on Saturday 2026-06-20
        assertEquals(20, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SATURDAY, nextTrigger.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun testCalculateNextTriggerTime_RecurringAlarm_OnlyActiveDayPast() {
        // Thursday 2026-06-18 10:00:00
        val now = getMockCalendar(2026, Calendar.JUNE, 18, 10, 0)
        // Alarm set for 09:30, active days: Thursday (4) only
        val alarm = Alarm(
            hour = 9,
            minute = 30,
            daysOfWeek = "4",
            isEnabled = true,
            puzzlesList = "MATH",
            puzzleCount = 1
        )
        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm, now)
        // Should schedule on next Thursday 2026-06-25
        assertEquals(25, nextTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.THURSDAY, nextTrigger.get(Calendar.DAY_OF_WEEK))
    }

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
