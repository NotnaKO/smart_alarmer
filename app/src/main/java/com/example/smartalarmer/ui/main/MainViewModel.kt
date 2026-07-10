package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiEvent {
    data class AlarmScheduled(val triggerAtMillis: Long) : MainUiEvent
    data object ExactAlarmPermissionRequired : MainUiEvent
    data class AlarmScheduleFailed(val exception: Exception) : MainUiEvent
}

class MainViewModel(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmSchedulingGateway
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = alarmRepository.alarms
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

    fun saveAlarm(
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        puzzlesList: String,
        puzzleCount: Int,
        isGradualVolume: Boolean,
        label: String,
        soundUri: String?
    ) {
        viewModelScope.launch {
            val current = _editingAlarm.value
            val scheduleResult = if (current != null) {
                val candidate = current.copy(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    label = label,
                    soundUri = soundUri,
                    isEnabled = true
                )
                scheduleAndPersist(candidate)
            } else {
                val draft = Alarm(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    label = label,
                    soundUri = soundUri,
                    isEnabled = false
                )
                val inserted = alarmRepository.insertAlarm(draft)
                scheduleAndPersist(inserted.copy(isEnabled = true))
            }

            publishScheduleResult(scheduleResult)
            closeEditSheet()
        }
    }

    fun toggleAlarm(alarm: Alarm, isChecked: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isChecked)
            if (isChecked) {
                val result = scheduleAndPersist(updated)
                if (result !is AlarmScheduleResult.Scheduled) {
                    publishScheduleResult(result)
                }
            } else {
                alarmRepository.updateAlarm(updated)
                alarmScheduler.cancel(updated)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmScheduler.cancel(alarm)
            alarmRepository.deleteAlarm(alarm)
        }
    }

    private suspend fun scheduleAndPersist(candidate: Alarm): AlarmScheduleResult {
        val result = alarmScheduler.schedule(candidate)
        val savedAlarm = if (result is AlarmScheduleResult.Scheduled) {
            candidate
        } else {
            alarmScheduler.cancel(candidate)
            candidate.copy(isEnabled = false)
        }
        alarmRepository.updateAlarm(savedAlarm)
        return result
    }

    private suspend fun publishScheduleResult(result: AlarmScheduleResult) {
        val event = when (result) {
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
