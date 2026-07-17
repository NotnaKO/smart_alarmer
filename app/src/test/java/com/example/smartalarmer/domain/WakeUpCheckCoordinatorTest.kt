package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.WakeUpCheckDao
import com.example.smartalarmer.data.WakeUpCheckSession
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.WakeUpCheckSchedulingGateway
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeUpCheckCoordinatorTest {
    @Test
    fun startCreatesFirstCheckFiveMinutesAfterDismissal() = runTest {
        val dao = FakeWakeUpCheckDao()
        val scheduler = FakeWakeUpCheckScheduler()
        val coordinator = coordinator(dao, scheduler, NOW)

        val result = coordinator.start(ALARM_ID)

        assertEquals(AlarmScheduleResult.Scheduled(NOW.toEpochMilli() + FIVE_MINUTES), result)
        val session = requireNotNull(dao.session)
        assertEquals(1, session.nextCheckNumber)
        assertEquals(3, session.totalChecks)
        assertEquals("fixed-token", session.token)
        assertEquals(NOW.toEpochMilli() + FIVE_MINUTES, session.nextTriggerAtMillis)
    }

    @Test
    fun completionSchedulesNextIntervalFromCompletionTime() = runTest {
        val dao = FakeWakeUpCheckDao()
        val scheduler = FakeWakeUpCheckScheduler()
        coordinator(dao, scheduler, NOW).start(ALARM_ID)
        val completedAt = NOW.plusSeconds(90)

        coordinator(dao, scheduler, completedAt).complete(ALARM_ID, "fixed-token", 1)

        val session = requireNotNull(dao.session)
        assertEquals(2, session.nextCheckNumber)
        assertEquals(completedAt.toEpochMilli() + FIVE_MINUTES, session.nextTriggerAtMillis)
    }

    @Test
    fun staleCompletionDoesNotAdvanceSession() = runTest {
        val dao = FakeWakeUpCheckDao()
        val scheduler = FakeWakeUpCheckScheduler()
        coordinator(dao, scheduler, NOW).start(ALARM_ID)

        val result = coordinator(dao, scheduler, NOW).complete(ALARM_ID, "stale-token", 1)

        assertNull(result)
        assertEquals(1, dao.session?.nextCheckNumber)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun completingFinalCheckClearsSession() = runTest {
        val dao = FakeWakeUpCheckDao()
        val scheduler = FakeWakeUpCheckScheduler()
        coordinator(dao, scheduler, NOW).start(ALARM_ID)
        repeat(2) { index ->
            coordinator(dao, scheduler, NOW).complete(ALARM_ID, "fixed-token", index + 1)
        }

        coordinator(dao, scheduler, NOW).complete(ALARM_ID, "fixed-token", 3)

        assertNull(dao.session)
        assertEquals(ALARM_ID, scheduler.cancelled.last())
    }

    @Test
    fun completingOnlyCheckDoesNotScheduleAnother() = runTest {
        val dao = FakeWakeUpCheckDao()
        val scheduler = FakeWakeUpCheckScheduler()
        coordinator(dao, scheduler, NOW, totalChecks = 1).start(ALARM_ID)

        val result = coordinator(dao, scheduler, NOW, totalChecks = 1).complete(ALARM_ID, "fixed-token", 1)

        assertNull(result)
        assertNull(dao.session)
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(ALARM_ID, scheduler.cancelled.last())
    }

    private fun coordinator(
        dao: FakeWakeUpCheckDao,
        scheduler: FakeWakeUpCheckScheduler,
        instant: Instant,
        totalChecks: Int = 3
    ) = WakeUpCheckCoordinator(
        alarmRepository = FakeAlarmRepository(totalChecks),
        sessionDao = dao,
        scheduler = scheduler,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        tokenFactory = { "fixed-token" }
    )

    private class FakeAlarmRepository(
        totalChecks: Int
    ) : AlarmRepository {
        private val alarm =
            Alarm(
                id = ALARM_ID,
                hour = 7,
                minute = 0,
                daysOfWeek = "",
                isEnabled = false,
                puzzlesList = "MATH,MEMORY",
                wakeUpChecksEnabled = true,
                wakeUpCheckCount = totalChecks,
                wakeUpCheckIntervalMinutes = 5
            )
        override val alarms: Flow<List<Alarm>> = flowOf(listOf(alarm))
        override suspend fun getEnabledAlarms() = emptyList<Alarm>()
        override suspend fun getAlarmById(id: Int) = alarm.takeIf { it.id == id }
        override suspend fun insertAlarm(alarm: Alarm) = alarm
        override suspend fun updateAlarm(alarm: Alarm) = Unit
        override suspend fun deleteAlarm(alarm: Alarm) = Unit
    }

    private class FakeWakeUpCheckDao : WakeUpCheckDao {
        var session: WakeUpCheckSession? = null
        override fun observeAllSessions(): Flow<List<WakeUpCheckSession>> = flowOf(listOfNotNull(session))
        override suspend fun getSession(alarmId: Int) = session?.takeIf { it.alarmId == alarmId }
        override suspend fun getAllSessions() = listOfNotNull(session)
        override suspend fun upsertSession(session: WakeUpCheckSession) {
            this.session = session
        }
        override suspend fun deleteSession(alarmId: Int) {
            if (session?.alarmId == alarmId) session = null
        }
    }

    private class FakeWakeUpCheckScheduler : WakeUpCheckSchedulingGateway {
        val scheduled = mutableListOf<WakeUpCheckSession>()
        val cancelled = mutableListOf<Int>()
        override fun schedule(session: WakeUpCheckSession): AlarmScheduleResult {
            scheduled += session
            return AlarmScheduleResult.Scheduled(session.nextTriggerAtMillis)
        }
        override fun cancel(alarmId: Int): AlarmCancelResult {
            cancelled += alarmId
            return AlarmCancelResult.Cancelled
        }
    }

    private companion object {
        const val ALARM_ID = 7
        const val FIVE_MINUTES = 300_000L
        val NOW: Instant = Instant.parse("2026-07-17T04:00:00Z")
    }
}
