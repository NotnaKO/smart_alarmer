package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.domain.AlarmActivationGate
import com.example.smartalarmer.scheduler.AlarmCancelResult
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
            label = "Morning",
            soundUri = null,
            volumeRampSeconds = 120,
            weekParity = "ODD"
        )
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertEquals(1, saved.id)
        assertTrue(saved.isEnabled)
        assertEquals(AlarmScheduleStatus.SCHEDULED.name, saved.scheduleStatus)
        assertEquals(12_345L, saved.scheduledTriggerAtMillis)
        assertEquals(120, saved.volumeRampSeconds)
        assertEquals("ODD", saved.weekParity)
        assertEquals(saved.id, scheduler.scheduled.single().id)
        assertEquals(MainUiEvent.AlarmScheduled(12_345L), event.await())
        assertFalse(viewModel.isBottomSheetVisible.value)
    }

    @Test
    fun saveNewAlarm_permissionMissing_removesDraftAndPublishesPermissionEvent() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        scheduler.nextResult = AlarmScheduleResult.PermissionRequired
        viewModel.openEditSheet()
        val event = async { viewModel.uiEvents.first() }

        viewModel.saveAlarm(
            hour = 8,
            minute = 0,
            daysOfWeek = "",
            puzzlesList = "MATH",
            puzzleCount = 1,
            label = "",
            soundUri = null
        )
        advanceUntilIdle()

        assertTrue(repository.alarms.value.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(MainUiEvent.ExactAlarmPermissionRequired, event.await())
        assertTrue(viewModel.isBottomSheetVisible.value)
    }

    @Test
    fun saveNewAlarm_scheduleFailure_removesDraftAndPublishesFailure() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val exception = IllegalStateException("alarm manager unavailable")
        scheduler.nextResult = AlarmScheduleResult.Failure(exception)
        viewModel.openEditSheet()
        val event = async { viewModel.uiEvents.first() }

        saveDefaultAlarm(viewModel)
        advanceUntilIdle()

        assertTrue(repository.alarms.value.isEmpty())
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(MainUiEvent.AlarmScheduleFailed(exception), event.await())
        assertTrue(viewModel.isBottomSheetVisible.value)
    }

    @Test
    fun saveEditedAlarm_success_updatesSameRowAndEnablesAlarm() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 8, isEnabled = false)
        repository.seed(existing)
        val viewModel = createViewModel()
        viewModel.openEditSheet(existing)
        val event = async { viewModel.uiEvents.first() }

        viewModel.saveAlarm(
            hour = 9,
            minute = 45,
            daysOfWeek = "7,1",
            puzzlesList = "typing,memory",
            puzzleCount = 3,
            label = "Updated",
            soundUri = "content://alarm/sound"
        )
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertEquals(8, saved.id)
        assertEquals(9, saved.hour)
        assertEquals(45, saved.minute)
        assertEquals("1,7", saved.daysOfWeek)
        assertEquals("TYPING,MEMORY", saved.puzzlesList)
        assertEquals(2, saved.puzzleCount)
        assertEquals("Updated", saved.label)
        assertEquals("content://alarm/sound", saved.soundUri)
        assertTrue(saved.isEnabled)
        assertEquals(saved.id, scheduler.scheduled.single().id)
        assertEquals(MainUiEvent.AlarmScheduled(1L), event.await())
    }

    @Test
    fun saveEditedAlarm_permissionDenied_preservesExistingRow() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 8, isEnabled = true)
        repository.seed(existing)
        scheduler.nextResult = AlarmScheduleResult.PermissionRequired
        val viewModel = createViewModel()
        viewModel.openEditSheet(existing)
        val event = async { viewModel.uiEvents.first() }

        saveDefaultAlarm(viewModel)
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertEquals(existing, saved)
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(MainUiEvent.ExactAlarmPermissionRequired, event.await())
        assertTrue(viewModel.isBottomSheetVisible.value)
    }

    @Test
    fun saveNewAlarm_normalizesMalformedLegacyConfiguration() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.saveAlarm(
            hour = 8,
            minute = 0,
            daysOfWeek = "7,1,1,invalid,9",
            puzzlesList = "shake,unknown,math,shake",
            puzzleCount = 2,
            label = "",
            soundUri = null
        )
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertEquals("1,7", saved.daysOfWeek)
        assertEquals("MATH,SHAKE", saved.puzzlesList)
    }

    @Test
    fun toggleAlarm_scheduleFailure_keepsAlarmDisabledAndPublishesFailure() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 9, isEnabled = false)
        repository.seed(existing)
        val exception = IllegalStateException("alarm manager unavailable")
        scheduler.nextResult = AlarmScheduleResult.Failure(exception)
        val viewModel = createViewModel()
        val event = async { viewModel.uiEvents.first() }

        viewModel.toggleAlarm(existing, isChecked = true)
        advanceUntilIdle()

        assertFalse(
            repository.alarms.value
                .single()
                .isEnabled
        )
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(MainUiEvent.AlarmScheduleFailed(exception), event.await())
    }

    @Test
    fun toggleAlarm_enableSuccess_persistsEnabledAlarmWithoutFailureEvent() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 10, isEnabled = false)
        repository.seed(existing)
        val viewModel = createViewModel()

        viewModel.toggleAlarm(existing, isChecked = true)
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertTrue(saved.isEnabled)
        assertEquals(saved.id, scheduler.scheduled.single().id)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun toggleAlarm_permissionDenied_keepsAlarmDisabledAndPublishesPermissionEvent() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 10, isEnabled = false)
        repository.seed(existing)
        scheduler.nextResult = AlarmScheduleResult.PermissionRequired
        val viewModel = createViewModel()
        val event = async { viewModel.uiEvents.first() }

        viewModel.toggleAlarm(existing, isChecked = true)
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertFalse(saved.isEnabled)
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(MainUiEvent.ExactAlarmPermissionRequired, event.await())
    }

    @Test
    fun toggleAlarm_disable_persistsDisabledAlarmAndCancelsSchedule() = runTest(mainDispatcherRule.dispatcher) {
        val existing = alarm(id = 10, isEnabled = true)
        repository.seed(existing)
        val viewModel = createViewModel()

        viewModel.toggleAlarm(existing, isChecked = false)
        advanceUntilIdle()

        val saved = repository.alarms.value.single()
        assertFalse(saved.isEnabled)
        assertEquals(existing, scheduler.cancelled.single())
        assertTrue(scheduler.scheduled.isEmpty())
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

    @Test
    fun notificationDeliveryUnavailable_blocksAlarmActivation() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = MainViewModel(repository, scheduler, AlarmActivationGate { false })
        viewModel.openEditSheet()
        val event = async { viewModel.uiEvents.first() }

        saveDefaultAlarm(viewModel)
        advanceUntilIdle()

        assertTrue(repository.alarms.value.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(MainUiEvent.NotificationCapabilityRequired, event.await())
    }

    private fun createViewModel() = MainViewModel(repository, scheduler)

    private fun saveDefaultAlarm(viewModel: MainViewModel) {
        viewModel.saveAlarm(
            hour = 7,
            minute = 30,
            daysOfWeek = "1,2,3,4,5",
            puzzlesList = "MATH",
            puzzleCount = 1,
            label = "Morning",
            soundUri = null
        )
    }

    private fun alarm(
        id: Int,
        isEnabled: Boolean = true
    ) = Alarm(
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
        alarms.value =
            alarms.value.map { existing ->
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

    override fun cancel(alarm: Alarm): AlarmCancelResult {
        cancelled += alarm
        return AlarmCancelResult.Cancelled
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
