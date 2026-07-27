package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.WakeUpCheckSession
import com.example.smartalarmer.domain.AlarmActivationGate
import com.example.smartalarmer.domain.AlarmCommandCoordinator
import com.example.smartalarmer.domain.AlarmCommandResult
import com.example.smartalarmer.domain.AlarmDays
import com.example.smartalarmer.domain.AlarmDraft
import com.example.smartalarmer.domain.AlarmVolumeRamp
import com.example.smartalarmer.domain.AlarmWeekParity
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import com.example.smartalarmer.scheduler.RescheduleEnabledAlarms
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiEvent {
    data class AlarmScheduled(
        val triggerAtMillis: Long
    ) : MainUiEvent

    data class AlarmSkipped(
        val nextTriggerAtMillis: Long
    ) : MainUiEvent

    data object ExactAlarmPermissionRequired : MainUiEvent

    data object NotificationCapabilityRequired : MainUiEvent

    data class AlarmScheduleFailed(
        val exception: Exception
    ) : MainUiEvent

    data class AlarmOperationFailed(
        val exception: Exception
    ) : MainUiEvent
}

data class AlarmCardState(
    val alarm: Alarm,
    val wakeUpCheckSession: WakeUpCheckSession?,
    val effectiveNextAtMillis: Long?
)

class MainViewModel(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmSchedulingGateway,
    activationGate: AlarmActivationGate = AlarmActivationGate.ALWAYS_READY,
    private val wakeUpCheckCoordinator: WakeUpCheckCoordinator? = null,
    wakeUpCheckSessionFlow: Flow<List<WakeUpCheckSession>> = flowOf(emptyList()),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {
    private val commandCoordinator = AlarmCommandCoordinator(alarmRepository, alarmScheduler, activationGate)
    private val rescheduleEnabledAlarms = RescheduleEnabledAlarms(alarmRepository, alarmScheduler)

    val alarms: StateFlow<List<Alarm>> =
        alarmRepository.alarms
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val wakeUpCheckSessions: StateFlow<List<WakeUpCheckSession>> =
        wakeUpCheckSessionFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val alarmCards: StateFlow<List<AlarmCardState>> =
        combine(alarmRepository.alarms, wakeUpCheckSessionFlow, ::sortedAlarmCards)
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
            if (current != null && result is AlarmCommandResult.Scheduled) {
                cancelWakeUpChecksAndPublish(current.id)
            }
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
        soundUri: String?,
        volumeRampSeconds: Int = AlarmVolumeRamp.DEFAULT_SECONDS,
        weekParity: String = AlarmWeekParity.EVERY.name
    ) {
        val selection = PuzzleSelection.parse(puzzlesList)
        saveAlarm(
            AlarmDraft(
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                repeatDays = AlarmDays.parse(daysOfWeek),
                repeatWeekParity = AlarmWeekParity.parse(weekParity),
                puzzleSelection = selection,
                puzzleCount = puzzleCount.coerceIn(1, selection.values.size),
                label = label,
                soundUri = soundUri,
                volumeRampSeconds = AlarmVolumeRamp.sanitize(volumeRampSeconds)
            )
        )
    }

    fun toggleAlarm(
        alarm: Alarm,
        isChecked: Boolean
    ) {
        viewModelScope.launch {
            val result = commandCoordinator.setEnabled(alarm, isChecked)
            if (result is AlarmCommandResult.Updated || result is AlarmCommandResult.Scheduled) {
                if (!cancelWakeUpChecksAndPublish(alarm.id)) {
                    if (!isChecked && result is AlarmCommandResult.Updated) {
                        publishCommandResult(
                            commandCoordinator.setEnabled(result.alarm, true),
                            publishSuccess = false
                        )
                    }
                    return@launch
                }
            }
            publishCommandResult(result, publishSuccess = isChecked)
        }
    }

    fun skipNextOccurrence(alarm: Alarm) {
        val triggerAtMillis = alarm.scheduledTriggerAtMillis ?: return
        val suppressedThroughEpochDay =
            Instant
                .ofEpochMilli(triggerAtMillis)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()
        viewModelScope.launch {
            val result = commandCoordinator.suppressThrough(alarm, suppressedThroughEpochDay)
            if (result is AlarmCommandResult.Scheduled) {
                if (!cancelWakeUpChecksAndPublish(alarm.id)) {
                    val rollback =
                        alarm.suppressedThroughEpochDay?.let {
                            commandCoordinator.suppressThrough(result.alarm, it)
                        } ?: commandCoordinator.clearSuppression(result.alarm)
                    publishCommandResult(rollback, publishSuccess = false)
                    return@launch
                }
                _uiEvents.send(MainUiEvent.AlarmSkipped(result.triggerAtMillis))
            } else {
                publishCommandResult(result, publishSuccess = false)
            }
        }
    }

    fun restoreSuppressedOccurrences(alarm: Alarm) {
        viewModelScope.launch {
            val result = commandCoordinator.clearSuppression(alarm)
            if (result is AlarmCommandResult.Scheduled) {
                _uiEvents.send(MainUiEvent.AlarmScheduled(result.triggerAtMillis))
            } else {
                publishCommandResult(result, publishSuccess = false)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val result = commandCoordinator.delete(alarm)
            publishCommandResult(result, publishSuccess = false)
            if (result is AlarmCommandResult.Deleted) {
                cancelWakeUpChecksAndPublish(alarm.id)
            }
        }
    }

    fun cancelWakeUpChecks(alarmId: Int) {
        viewModelScope.launch {
            cancelWakeUpChecksAndPublish(alarmId)
        }
    }

    fun reconcileEnabledAlarms() {
        viewModelScope.launch {
            try {
                val report = rescheduleEnabledAlarms()
                for (failure in report.failures) {
                    publishScheduleResult(failure.result)
                }
            } catch (e: Exception) {
                _uiEvents.send(MainUiEvent.AlarmOperationFailed(e))
            }
        }
    }

    fun reconcileWakeUpChecks() {
        viewModelScope.launch {
            try {
                wakeUpCheckCoordinator
                    ?.restoreAll()
                    ?.filterNot { it is AlarmScheduleResult.Scheduled }
                    ?.forEach { publishScheduleResult(it) }
            } catch (error: Exception) {
                _uiEvents.send(MainUiEvent.AlarmOperationFailed(error))
            }
        }
    }

    private suspend fun cancelWakeUpChecksAndPublish(alarmId: Int): Boolean = when (
        val result = wakeUpCheckCoordinator?.cancel(alarmId)
    ) {
        is AlarmCancelResult.Failure -> {
            _uiEvents.send(MainUiEvent.AlarmOperationFailed(result.exception))
            false
        }
        AlarmCancelResult.Cancelled,
        null
        -> true
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
            AlarmCommandResult.NotificationCapabilityRequired ->
                _uiEvents.send(MainUiEvent.NotificationCapabilityRequired)
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
        private val alarmScheduler: AlarmSchedulingGateway,
        private val activationGate: AlarmActivationGate = AlarmActivationGate.ALWAYS_READY,
        private val wakeUpCheckCoordinator: WakeUpCheckCoordinator? = null,
        private val wakeUpCheckSessionFlow: Flow<List<WakeUpCheckSession>> = flowOf(emptyList())
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    alarmRepository,
                    alarmScheduler,
                    activationGate,
                    wakeUpCheckCoordinator,
                    wakeUpCheckSessionFlow
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

internal fun sortedAlarmCards(
    alarms: List<Alarm>,
    sessions: List<WakeUpCheckSession>
): List<AlarmCardState> {
    val sessionsByAlarmId = sessions.associateBy(WakeUpCheckSession::alarmId)
    return alarms
        .map { alarm ->
            val session = sessionsByAlarmId[alarm.id]
            val mainTrigger = alarm.scheduledTriggerAtMillis.takeIf { alarm.isEnabled }
            AlarmCardState(
                alarm = alarm,
                wakeUpCheckSession = session,
                effectiveNextAtMillis =
                listOfNotNull(mainTrigger, session?.nextTriggerAtMillis).minOrNull()
            )
        }.sortedWith(
            compareBy<AlarmCardState> { card ->
                when {
                    card.effectiveNextAtMillis != null -> 0
                    card.alarm.isEnabled -> 1
                    else -> 2
                }
            }.thenBy { it.effectiveNextAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.alarm.hour }
                .thenBy { it.alarm.minute }
                .thenBy { it.alarm.id }
        )
}
