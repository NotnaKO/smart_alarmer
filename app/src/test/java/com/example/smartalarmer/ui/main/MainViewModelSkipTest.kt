package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.WakeUpCheckDao
import com.example.smartalarmer.data.WakeUpCheckSession
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import com.example.smartalarmer.scheduler.WakeUpCheckSchedulingGateway
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSkipTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun skipReschedulesAlarmAndCancelsActiveWakeUpChecks() = runTest(mainDispatcherRule.dispatcher) {
        val originalTrigger = Instant.parse("2026-07-27T07:00:00Z").toEpochMilli()
        val replacementTrigger = Instant.parse("2026-07-28T07:00:00Z").toEpochMilli()
        val alarm = alarm(originalTrigger)
        val repository = SkipRepository(alarm)
        val sessionDao = SkipSessionDao(session(alarm.id))
        val wakeScheduler = SkipWakeScheduler()
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger),
                wakeUpCheckCoordinator =
                WakeUpCheckCoordinator(
                    alarmRepository = repository,
                    sessionDao = sessionDao,
                    scheduler = wakeScheduler
                ),
                wakeUpCheckSessionFlow = sessionDao.observeAllSessions(),
                zoneIdProvider = { ZoneId.of("UTC") }
            )
        val event = async { viewModel.uiEvents.first() }

        viewModel.skipNextOccurrence(alarm)
        advanceUntilIdle()

        val updated = repository.items.value.single()
        assertTrue(updated.isEnabled)
        assertEquals(
            LocalDate.parse("2026-07-27").toEpochDay(),
            updated.suppressedThroughEpochDay
        )
        assertEquals(replacementTrigger, updated.scheduledTriggerAtMillis)
        assertNull(sessionDao.current.value)
        assertEquals(listOf(alarm.id), wakeScheduler.cancelled)
        assertEquals(MainUiEvent.AlarmSkipped(replacementTrigger), event.await())
    }

    @Test
    fun skipResolvesZoneWhenActionRuns() = runTest(mainDispatcherRule.dispatcher) {
        val originalTrigger = Instant.parse("2026-07-28T15:30:00Z").toEpochMilli()
        val replacementTrigger = Instant.parse("2026-07-29T15:30:00Z").toEpochMilli()
        val alarm = alarm(originalTrigger)
        val repository = SkipRepository(alarm)
        var currentZone = ZoneId.of("UTC")
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger),
                zoneIdProvider = { currentZone }
            )
        currentZone = ZoneId.of("Asia/Tokyo")

        viewModel.skipNextOccurrence(alarm)
        advanceUntilIdle()

        assertEquals(
            LocalDate.parse("2026-07-29").toEpochDay(),
            repository.items.value.single().suppressedThroughEpochDay
        )
    }

    @Test
    fun pauseThroughDateReschedulesAfterSelectedDateAndCancelsChecks() = runTest(mainDispatcherRule.dispatcher) {
        val originalTrigger = Instant.parse("2026-07-28T07:00:00Z").toEpochMilli()
        val replacementTrigger = Instant.parse("2026-08-04T07:00:00Z").toEpochMilli()
        val alarm = alarm(originalTrigger)
        val repository = SkipRepository(alarm)
        val sessionDao = SkipSessionDao(session(alarm.id))
        val wakeScheduler = SkipWakeScheduler()
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger),
                wakeUpCheckCoordinator =
                WakeUpCheckCoordinator(
                    alarmRepository = repository,
                    sessionDao = sessionDao,
                    scheduler = wakeScheduler
                ),
                wakeUpCheckSessionFlow = sessionDao.observeAllSessions(),
                zoneIdProvider = { ZoneId.of("UTC") }
            )
        val event = async { viewModel.uiEvents.first() }
        val pauseThrough = LocalDate.parse("2026-08-03").toEpochDay()

        viewModel.pauseThroughDate(alarm, pauseThrough)
        advanceUntilIdle()

        val updated = repository.items.value.single()
        assertTrue(updated.isEnabled)
        assertEquals(pauseThrough, updated.suppressedThroughEpochDay)
        assertEquals(replacementTrigger, updated.scheduledTriggerAtMillis)
        assertNull(sessionDao.current.value)
        assertEquals(listOf(alarm.id), wakeScheduler.cancelled)
        assertEquals(MainUiEvent.AlarmPaused(replacementTrigger), event.await())
    }

    @Test
    fun pauseThroughDateCannotPrecedeScheduledOccurrence() = runTest(mainDispatcherRule.dispatcher) {
        val originalTrigger = Instant.parse("2026-07-28T23:30:00Z").toEpochMilli()
        val replacementTrigger = Instant.parse("2026-07-29T23:30:00Z").toEpochMilli()
        val alarm = alarm(originalTrigger)
        val repository = SkipRepository(alarm)
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger),
                zoneIdProvider = { ZoneId.of("Asia/Tokyo") }
            )

        viewModel.pauseThroughDate(
            alarm,
            LocalDate.parse("2026-07-01").toEpochDay()
        )
        advanceUntilIdle()

        assertEquals(
            LocalDate.parse("2026-07-29").toEpochDay(),
            repository.items.value.single().suppressedThroughEpochDay
        )
    }

    @Test
    fun disablingAlarmAlsoCancelsActiveWakeUpChecks() = runTest(mainDispatcherRule.dispatcher) {
        val alarm = alarm(Instant.parse("2026-07-27T07:00:00Z").toEpochMilli())
        val repository = SkipRepository(alarm)
        val sessionDao = SkipSessionDao(session(alarm.id))
        val wakeScheduler = SkipWakeScheduler()
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(0L),
                wakeUpCheckCoordinator =
                WakeUpCheckCoordinator(
                    alarmRepository = repository,
                    sessionDao = sessionDao,
                    scheduler = wakeScheduler
                ),
                wakeUpCheckSessionFlow = sessionDao.observeAllSessions()
            )

        viewModel.toggleAlarm(alarm, false)
        advanceUntilIdle()

        assertFalse(repository.items.value.single().isEnabled)
        assertNull(sessionDao.current.value)
        assertEquals(listOf(alarm.id), wakeScheduler.cancelled)
    }

    @Test
    fun failedWakeUpCheckCancellationRollsBackSkip() = runTest(mainDispatcherRule.dispatcher) {
        val originalTrigger = Instant.parse("2026-07-27T07:00:00Z").toEpochMilli()
        val replacementTrigger = Instant.parse("2026-07-28T07:00:00Z").toEpochMilli()
        val alarm = alarm(originalTrigger)
        val repository = SkipRepository(alarm)
        val sessionDao = SkipSessionDao(session(alarm.id))
        val cancellationError = IllegalStateException("cancel failed")
        val wakeScheduler =
            SkipWakeScheduler(AlarmCancelResult.Failure(cancellationError))
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger, originalTrigger),
                wakeUpCheckCoordinator =
                WakeUpCheckCoordinator(
                    alarmRepository = repository,
                    sessionDao = sessionDao,
                    scheduler = wakeScheduler
                ),
                wakeUpCheckSessionFlow = sessionDao.observeAllSessions(),
                zoneIdProvider = { ZoneId.of("UTC") }
            )
        val event = async { viewModel.uiEvents.first() }

        viewModel.skipNextOccurrence(alarm)
        advanceUntilIdle()

        val restored = repository.items.value.single()
        assertNull(restored.suppressedThroughEpochDay)
        assertEquals(originalTrigger, restored.scheduledTriggerAtMillis)
        assertEquals(MainUiEvent.AlarmOperationFailed(cancellationError), event.await())
    }

    @Test
    fun resumingScheduleUsesStandardScheduledConfirmation() = runTest(mainDispatcherRule.dispatcher) {
        val replacementTrigger = Instant.parse("2026-07-28T07:00:00Z").toEpochMilli()
        val alarm =
            alarm(replacementTrigger)
                .copy(suppressedThroughEpochDay = LocalDate.parse("2026-07-27").toEpochDay())
        val repository = SkipRepository(alarm)
        val viewModel =
            MainViewModel(
                alarmRepository = repository,
                alarmScheduler = SkipMainScheduler(replacementTrigger),
                zoneIdProvider = { ZoneId.of("UTC") }
            )
        val event = async { viewModel.uiEvents.first() }

        viewModel.restoreSuppressedOccurrences(alarm)
        advanceUntilIdle()

        assertNull(repository.items.value.single().suppressedThroughEpochDay)
        assertEquals(MainUiEvent.AlarmScheduled(replacementTrigger), event.await())
    }

    private fun alarm(triggerAtMillis: Long) = Alarm(
        id = 7,
        hour = 7,
        minute = 0,
        daysOfWeek = "1,2,3,4,5,6,7",
        puzzlesList = "MATH",
        wakeUpChecksEnabled = true,
        scheduledTriggerAtMillis = triggerAtMillis
    )

    private fun session(alarmId: Int) = WakeUpCheckSession(
        alarmId = alarmId,
        token = "token",
        nextCheckNumber = 1,
        totalChecks = 3,
        intervalMinutes = 5,
        nextTriggerAtMillis = 100L,
        puzzlesList = "MATH",
        soundUri = null,
        alarmLabel = ""
    )
}

private class SkipRepository(initial: Alarm) : AlarmRepository {
    val items = MutableStateFlow(listOf(initial))
    override val alarms: Flow<List<Alarm>> = items

    override suspend fun getEnabledAlarms(): List<Alarm> = items.value.filter(Alarm::isEnabled)
    override suspend fun getAlarmById(id: Int): Alarm? = items.value.firstOrNull { it.id == id }
    override suspend fun insertAlarm(alarm: Alarm): Alarm = alarm
    override suspend fun updateAlarm(alarm: Alarm) {
        items.value = items.value.map { if (it.id == alarm.id) alarm else it }
    }
    override suspend fun deleteAlarm(alarm: Alarm) = Unit
}

private class SkipMainScheduler(
    vararg triggers: Long
) : AlarmSchedulingGateway {
    private val scheduledTriggers = ArrayDeque(triggers.toList())

    override fun schedule(alarm: Alarm): AlarmScheduleResult = AlarmScheduleResult.Scheduled(
        scheduledTriggers.removeFirst()
    )

    override fun cancel(alarm: Alarm): AlarmCancelResult = AlarmCancelResult.Cancelled
}

private class SkipSessionDao(
    initial: WakeUpCheckSession?
) : WakeUpCheckDao {
    val current = MutableStateFlow(initial)

    override fun observeAllSessions(): Flow<List<WakeUpCheckSession>> = MutableStateFlow(
        listOfNotNull(current.value)
    )

    override suspend fun getSession(alarmId: Int): WakeUpCheckSession? = current.value?.takeIf {
        it.alarmId == alarmId
    }

    override suspend fun getAllSessions(): List<WakeUpCheckSession> = listOfNotNull(current.value)

    override suspend fun upsertSession(session: WakeUpCheckSession) {
        current.value = session
    }

    override suspend fun deleteSession(alarmId: Int) {
        if (current.value?.alarmId == alarmId) current.value = null
    }
}

private class SkipWakeScheduler(
    private val cancelResult: AlarmCancelResult = AlarmCancelResult.Cancelled
) : WakeUpCheckSchedulingGateway {
    val cancelled = mutableListOf<Int>()

    override fun schedule(session: WakeUpCheckSession): AlarmScheduleResult = AlarmScheduleResult.Scheduled(
        session.nextTriggerAtMillis
    )

    override fun cancel(alarmId: Int): AlarmCancelResult {
        cancelled += alarmId
        return cancelResult
    }
}
