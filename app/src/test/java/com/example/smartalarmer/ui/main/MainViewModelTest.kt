package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()

    @Test
    fun initialState_isClosedAndNotEditing() {
        val viewModel = createViewModel()

        assertFalse(viewModel.isBottomSheetVisible.value)
        assertEquals(null, viewModel.editingAlarm.value)
    }

    @Test
    fun editSheetState_opensAndClosesWithAlarm() {
        val viewModel = createViewModel()
        val alarm = alarm(id = 7)

        viewModel.openEditSheet(alarm)
        assertTrue(viewModel.isBottomSheetVisible.value)
        assertEquals(alarm, viewModel.editingAlarm.value)

        viewModel.closeEditSheet()
        assertFalse(viewModel.isBottomSheetVisible.value)
        assertEquals(null, viewModel.editingAlarm.value)
    }

    @Test
    fun saveNewAlarm_success_enablesAlarmAndPublishesTrigger() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        scheduler.nextResult = AlarmScheduleResult.Scheduled(12_345L)
        viewModel.openEditSheet()
        val event = async { viewModel.uiEvents.first() }

        viewModel.saveAlarm(
            hour = 7,
            minute = 30,
            daysOfWeek = "1,2,3,4,5",
            puzzlesList = "MATH",
            puzzleCount = 1,
            isGradualVolume = true,
            label = "Morning",
            soundUri = null
        )
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertEquals(1, saved.id)
        assertTrue(saved.isEnabled)
        assertEquals(saved, scheduler.scheduled.single())
        assertEquals(MainUiEvent.AlarmScheduled(12_345L), event.await())
        assertFalse(viewModel.isBottomSheetVisible.value)
    }

    @Test
    fun saveNewAlarm_permissionMissing_persistsDisabledAndPublishesPermissionEvent() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            scheduler.nextResult = AlarmScheduleResult.PermissionRequired
            val event = async { viewModel.uiEvents.first() }

            viewModel.saveAlarm(
                hour = 8,
                minute = 0,
                daysOfWeek = "",
                puzzlesList = "MATH",
                puzzleCount = 1,
                isGradualVolume = false,
                label = "",
                soundUri = null
            )
            advanceUntilIdle()

            val saved = repository.alarms.value.single()
            assertFalse(saved.isEnabled)
            assertEquals(saved.id, scheduler.cancelled.single().id)
            assertEquals(MainUiEvent.ExactAlarmPermissionRequired, event.await())
        }

    @Test
    fun saveNewAlarm_normalizesMalformedLegacyConfiguration() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.saveAlarm(
                hour = 8,
                minute = 0,
                daysOfWeek = "7,1,1,invalid,9",
                puzzlesList = "shake,unknown,math,shake",
                puzzleCount = 2,
                isGradualVolume = true,
                label = "",
                soundUri = null
            )
            advanceUntilIdle()

            val saved = repository.alarms.value.single()
            assertEquals("1,7", saved.daysOfWeek)
            assertEquals("MATH,SHAKE", saved.puzzlesList)
        }

    @Test
    fun toggleAlarm_scheduleFailure_keepsAlarmDisabledAndPublishesFailure() =
        runTest(mainDispatcherRule.dispatcher) {
            val existing = alarm(id = 9, isEnabled = false)
            repository.seed(existing)
            val exception = IllegalStateException("alarm manager unavailable")
            scheduler.nextResult = AlarmScheduleResult.Failure(exception)
            val viewModel = createViewModel()
            val event = async { viewModel.uiEvents.first() }

            viewModel.toggleAlarm(existing, isChecked = true)
            advanceUntilIdle()

            assertFalse(repository.alarms.value.single().isEnabled)
            assertEquals(9, scheduler.cancelled.single().id)
            assertEquals(MainUiEvent.AlarmScheduleFailed(exception), event.await())
        }

    @Test
    fun deleteAlarm_cancelsAndDeletesPersistedAlarm() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 11)
        repository.seed(existing)
        val viewModel = createViewModel()

        viewModel.deleteAlarm(existing)
        advanceUntilIdle()

        assertTrue(repository.alarms.value.isEmpty())
        assertEquals(existing, scheduler.cancelled.single())
    }

    private fun createViewModel() = MainViewModel(repository, scheduler)

    private fun alarm(id: Int, isEnabled: Boolean = true) = Alarm(
        id = id,
        hour = 7,
        minute = 30,
        daysOfWeek = "1,2,3,4,5",
        isEnabled = isEnabled,
        puzzlesList = "MATH",
        puzzleCount = 1
    )
}

private class FakeAlarmRepository : AlarmRepository {
    override val alarms = MutableStateFlow<List<Alarm>>(emptyList())
    private var nextId = 1

    fun seed(alarm: Alarm) {
        alarms.value = listOf(alarm)
        nextId = maxOf(nextId, alarm.id + 1)
    }

    override suspend fun getEnabledAlarms(): List<Alarm> = alarms.value.filter { it.isEnabled }

    override suspend fun getAlarmById(id: Int): Alarm? = alarms.value.find { it.id == id }

    override suspend fun insertAlarm(alarm: Alarm): Alarm {
        val inserted = alarm.copy(id = nextId++)
        alarms.value += inserted
        return inserted
    }

    override suspend fun updateAlarm(alarm: Alarm) {
        alarms.value = alarms.value.map { existing ->
            if (existing.id == alarm.id) alarm else existing
        }
    }

    override suspend fun deleteAlarm(alarm: Alarm) {
        alarms.value = alarms.value.filterNot { it.id == alarm.id }
    }
}

private class FakeAlarmScheduler : AlarmSchedulingGateway {
    var nextResult: AlarmScheduleResult = AlarmScheduleResult.Scheduled(1L)
    val scheduled = mutableListOf<Alarm>()
    val cancelled = mutableListOf<Alarm>()

    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        scheduled += alarm
        return nextResult
    }

    override fun cancel(alarm: Alarm) {
        cancelled += alarm
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
