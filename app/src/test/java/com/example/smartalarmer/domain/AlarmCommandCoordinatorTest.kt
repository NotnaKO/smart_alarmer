package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCommandCoordinatorTest {
    @Test
    fun createSchedulesBeforeEnablingPersistedAlarm() = runTest {
        val repository = FakeRepository()
        val scheduler = FakeScheduler()

        val result = AlarmCommandCoordinator(repository, scheduler).create(draft())

        assertTrue(result is AlarmCommandResult.Scheduled)
        assertEquals(
            true,
            repository.items.value
                .single()
                .isEnabled
        )
        assertEquals(repository.items.value.single(), scheduler.scheduled.single())
    }

    @Test
    fun createScheduleFailureRemovesDisabledDraft() = runTest {
        val repository = FakeRepository()
        val scheduler =
            FakeScheduler(
                scheduleResult = AlarmScheduleResult.Failure(IllegalStateException("system"))
            )

        val result = AlarmCommandCoordinator(repository, scheduler).create(draft())

        assertTrue(result is AlarmCommandResult.SchedulingFailed)
        assertEquals(emptyList<Alarm>(), repository.items.value)
    }

    @Test
    fun createRollbackCancellationFailureKeepsDisabledRowAndSurfacesFailure() = runTest {
        val repository = FakeRepository().apply { failUpdates = true }
        val cancellationException = IllegalStateException("cancel")
        val scheduler =
            FakeScheduler(
                cancelResult = AlarmCancelResult.Failure(cancellationException)
            )

        val result = AlarmCommandCoordinator(repository, scheduler).create(draft())

        assertEquals(AlarmCommandResult.CancellationFailed(cancellationException), result)
        assertEquals(false, repository.items.value.single().isEnabled)
    }

    @Test
    fun editPersistenceFailureRestoresPreviousSchedule() = runTest {
        val original = alarm(id = 7, hour = 6)
        val repository = FakeRepository(listOf(original)).apply { failUpdates = true }
        val scheduler = FakeScheduler()

        val result =
            AlarmCommandCoordinator(repository, scheduler).update(
                original,
                draft(hour = 9)
            )

        assertTrue(result is AlarmCommandResult.PersistenceFailed)
        assertEquals(listOf(9, 6), scheduler.scheduled.map(Alarm::hour))
        assertEquals(original, repository.items.value.single())
    }

    @Test
    fun enablePersistenceFailureCancelsNewSchedule() = runTest {
        val original = alarm(id = 4, isEnabled = false)
        val repository = FakeRepository(listOf(original)).apply { failUpdates = true }
        val scheduler = FakeScheduler()

        val result = AlarmCommandCoordinator(repository, scheduler).setEnabled(original, true)

        assertTrue(result is AlarmCommandResult.PersistenceFailed)
        assertEquals(listOf(4), scheduler.cancelled.map(Alarm::id))
    }

    @Test
    fun enableRollbackCancellationFailureSurfacesFailure() = runTest {
        val original = alarm(id = 4, isEnabled = false)
        val repository = FakeRepository(listOf(original)).apply { failUpdates = true }
        val cancellationException = IllegalStateException("cancel")
        val scheduler =
            FakeScheduler(
                cancelResult = AlarmCancelResult.Failure(cancellationException)
            )

        val result = AlarmCommandCoordinator(repository, scheduler).setEnabled(original, true)

        assertEquals(AlarmCommandResult.CancellationFailed(cancellationException), result)
        assertEquals(original, repository.items.value.single())
    }

    @Test
    fun disableCancellationFailurePreservesEnabledRow() = runTest {
        val original = alarm(id = 3)
        val repository = FakeRepository(listOf(original))
        val scheduler =
            FakeScheduler(
                cancelResult = AlarmCancelResult.Failure(IllegalStateException("cancel"))
            )

        val result = AlarmCommandCoordinator(repository, scheduler).setEnabled(original, false)

        assertTrue(result is AlarmCommandResult.CancellationFailed)
        assertEquals(
            true,
            repository.items.value
                .single()
                .isEnabled
        )
    }

    @Test
    fun deletePersistenceFailureRestoresEnabledSchedule() = runTest {
        val original = alarm(id = 8)
        val repository = FakeRepository(listOf(original)).apply { failDeletes = true }
        val scheduler = FakeScheduler()

        val result = AlarmCommandCoordinator(repository, scheduler).delete(original)

        assertTrue(result is AlarmCommandResult.PersistenceFailed)
        assertEquals(original, scheduler.scheduled.single())
    }

    private fun draft(hour: Int = 7) = AlarmDraft(
        hour = hour,
        minute = 30,
        repeatDays = AlarmDays.ONE_TIME,
        puzzleSelection = PuzzleSelection.DEFAULT,
        puzzleCount = 1,
        label = "Wake up",
        soundUri = null
    )

    private fun alarm(
        id: Int,
        hour: Int = 7,
        isEnabled: Boolean = true
    ) = Alarm(
        id = id,
        hour = hour,
        minute = 30,
        daysOfWeek = "",
        isEnabled = isEnabled,
        puzzlesList = "MATH"
    )
}

private class FakeRepository(
    initial: List<Alarm> = emptyList()
) : AlarmRepository {
    val items = MutableStateFlow(initial)
    var failUpdates = false
    var failDeletes = false
    private var nextId = (initial.maxOfOrNull(Alarm::id) ?: 0) + 1

    override val alarms: Flow<List<Alarm>> = items

    override suspend fun getEnabledAlarms(): List<Alarm> = items.value.filter(Alarm::isEnabled)

    override suspend fun getAlarmById(id: Int): Alarm? = items.value.firstOrNull { it.id == id }

    override suspend fun insertAlarm(alarm: Alarm): Alarm {
        val inserted = alarm.copy(id = nextId++)
        items.value += inserted
        return inserted
    }

    override suspend fun updateAlarm(alarm: Alarm) {
        if (failUpdates) throw IllegalStateException("database update")
        items.value = items.value.map { if (it.id == alarm.id) alarm else it }
    }

    override suspend fun deleteAlarm(alarm: Alarm) {
        if (failDeletes) throw IllegalStateException("database delete")
        items.value = items.value.filterNot { it.id == alarm.id }
    }
}

private class FakeScheduler(
    var scheduleResult: AlarmScheduleResult = AlarmScheduleResult.Scheduled(123L),
    var cancelResult: AlarmCancelResult = AlarmCancelResult.Cancelled
) : AlarmSchedulingGateway {
    val scheduled = mutableListOf<Alarm>()
    val cancelled = mutableListOf<Alarm>()

    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        scheduled += alarm
        return scheduleResult
    }

    override fun cancel(alarm: Alarm): AlarmCancelResult {
        cancelled += alarm
        return cancelResult
    }
}
