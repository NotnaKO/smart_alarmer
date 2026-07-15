package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.domain.AlarmCommandCoordinator
import com.example.smartalarmer.domain.AlarmCommandResult
import com.example.smartalarmer.domain.AlarmDays
import com.example.smartalarmer.domain.AlarmDraft
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import com.example.smartalarmer.scheduler.RescheduleEnabledAlarms
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiEvent {
    data class AlarmScheduled(
        val triggerAtMillis: Long
    ) : MainUiEvent

    data object ExactAlarmPermissionRequired : MainUiEvent

    data class AlarmScheduleFailed(
        val exception: Exception
    ) : MainUiEvent

    data class AlarmOperationFailed(
        val exception: Exception
    ) : MainUiEvent
}

class MainViewModel(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmSchedulingGateway
) : ViewModel() {
    private val commandCoordinator = AlarmCommandCoordinator(alarmRepository, alarmScheduler)
    private val rescheduleEnabledAlarms = RescheduleEnabledAlarms(alarmRepository, alarmScheduler)

    val alarms: StateFlow<List<Alarm>> =
        alarmRepository.alarms
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _isBottomSheetVisible = MutableStateFlow(false)
    val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

    private val _editingAlarm = MutableStateFlow<Alarm?>(null)
    val editingAlarm = _editingAlarm.asStateFlow()

    private val _uiEvents = Channel<MainUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun openEditSheet(alarm: Alarm? = null) {
        _editingAlarm.value = alarm
        _isBottomSheetVisible.value = true
    }

    fun closeEditSheet() {
        _editingAlarm.value = null
        _isBottomSheetVisible.value = false
    }

    fun saveAlarm(draft: AlarmDraft) {
        viewModelScope.launch {
            val current = _editingAlarm.value
            val result =
                if (current == null) {
                    commandCoordinator.create(draft)
                } else {
                    commandCoordinator.update(current, draft)
                }
            publishCommandResult(result, publishSuccess = true)
            if (result is AlarmCommandResult.Scheduled) closeEditSheet()
        }
    }

    fun saveAlarm(
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        puzzlesList: String,
        puzzleCount: Int,
        label: String,
        soundUri: String?
    ) {
        val selection = PuzzleSelection.parse(puzzlesList)
        saveAlarm(
            AlarmDraft(
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                repeatDays = AlarmDays.parse(daysOfWeek),
                puzzleSelection = selection,
                puzzleCount = puzzleCount.coerceIn(1, selection.values.size),
                label = label,
                soundUri = soundUri
            )
        )
    }

    fun toggleAlarm(
        alarm: Alarm,
        isChecked: Boolean
    ) {
        viewModelScope.launch {
            val result = commandCoordinator.setEnabled(alarm, isChecked)
            publishCommandResult(result, publishSuccess = false)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            publishCommandResult(commandCoordinator.delete(alarm), publishSuccess = false)
        }
    }

    fun reconcileEnabledAlarms() {
        viewModelScope.launch {
            try {
                val report = rescheduleEnabledAlarms()
                for (failure in report.failures) {
                    publishScheduleResult(failure)
                }
            } catch (e: Exception) {
                _uiEvents.send(MainUiEvent.AlarmOperationFailed(e))
            }
        }
    }

    private suspend fun publishCommandResult(
        result: AlarmCommandResult,
        publishSuccess: Boolean
    ) {
        when (result) {
            is AlarmCommandResult.Scheduled ->
                if (publishSuccess) {
                    _uiEvents.send(MainUiEvent.AlarmScheduled(result.triggerAtMillis))
                }
            is AlarmCommandResult.Updated,
            AlarmCommandResult.Deleted
            -> Unit
            AlarmCommandResult.PermissionRequired ->
                _uiEvents.send(MainUiEvent.ExactAlarmPermissionRequired)
            is AlarmCommandResult.SchedulingFailed ->
                _uiEvents.send(MainUiEvent.AlarmScheduleFailed(result.exception))
            is AlarmCommandResult.PersistenceFailed ->
                _uiEvents.send(MainUiEvent.AlarmOperationFailed(result.exception))
            is AlarmCommandResult.CancellationFailed ->
                _uiEvents.send(MainUiEvent.AlarmOperationFailed(result.exception))
        }
    }

    private suspend fun publishScheduleResult(result: AlarmScheduleResult) {
        val event =
            when (result) {
                is AlarmScheduleResult.Scheduled -> MainUiEvent.AlarmScheduled(result.triggerAtMillis)
                AlarmScheduleResult.PermissionRequired -> MainUiEvent.ExactAlarmPermissionRequired
                is AlarmScheduleResult.Failure -> MainUiEvent.AlarmScheduleFailed(result.exception)
            }
        _uiEvents.send(event)
    }

    class Factory(
        private val alarmRepository: AlarmRepository,
        private val alarmScheduler: AlarmSchedulingGateway
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(alarmRepository, alarmScheduler) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
