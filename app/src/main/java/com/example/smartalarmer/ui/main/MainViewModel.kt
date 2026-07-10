package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

class MainViewModel(private val alarmDao: AlarmDao) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isBottomSheetVisible = MutableStateFlow(false)
    val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

    private val _editingAlarm = MutableStateFlow<Alarm?>(null)
    val editingAlarm = _editingAlarm.asStateFlow()

    fun openEditSheet(alarm: Alarm? = null) {
        _editingAlarm.value = alarm
        _isBottomSheetVisible.value = true
    }

    fun closeEditSheet() {
        _editingAlarm.value = null
        _isBottomSheetVisible.value = false
    }

    fun saveAlarm(
        context: Context,
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
                val result = AlarmScheduler.schedule(context, candidate)
                val savedAlarm = if (result is AlarmScheduleResult.Scheduled) {
                    candidate
                } else {
                    AlarmScheduler.cancel(context, candidate)
                    candidate.copy(isEnabled = false)
                }
                alarmDao.updateAlarm(savedAlarm)
                result
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
                val generatedId = alarmDao.insertAlarm(draft).toInt()
                val candidate = draft.copy(id = generatedId, isEnabled = true)
                val result = AlarmScheduler.schedule(context, candidate)
                val savedAlarm = if (result is AlarmScheduleResult.Scheduled) {
                    candidate
                } else {
                    AlarmScheduler.cancel(context, candidate)
                    candidate.copy(isEnabled = false)
                }
                alarmDao.updateAlarm(savedAlarm)
                result
            }

            when (scheduleResult) {
                is AlarmScheduleResult.Scheduled -> {
                    showScheduledToast(context, scheduleResult.triggerAtMillis)
                }
                AlarmScheduleResult.PermissionRequired -> {
                    showToast(context, com.example.smartalarmer.R.string.exact_alarm_permission_required)
                }
                is AlarmScheduleResult.Failure -> {
                    android.util.Log.e("MainViewModel", "Unable to schedule alarm", scheduleResult.exception)
                    showToast(context, com.example.smartalarmer.R.string.alarm_schedule_failed)
                }
            }

            closeEditSheet()
        }
    }

    fun toggleAlarm(context: Context, alarm: Alarm, isChecked: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isChecked)
            if (isChecked) {
                when (val result = AlarmScheduler.schedule(context, updated)) {
                    is AlarmScheduleResult.Scheduled -> alarmDao.updateAlarm(updated)
                    AlarmScheduleResult.PermissionRequired -> {
                        AlarmScheduler.cancel(context, updated)
                        alarmDao.updateAlarm(updated.copy(isEnabled = false))
                        showToast(context, com.example.smartalarmer.R.string.exact_alarm_permission_required)
                    }
                    is AlarmScheduleResult.Failure -> {
                        AlarmScheduler.cancel(context, updated)
                        alarmDao.updateAlarm(updated.copy(isEnabled = false))
                        android.util.Log.e("MainViewModel", "Unable to enable alarm", result.exception)
                        showToast(context, com.example.smartalarmer.R.string.alarm_schedule_failed)
                    }
                }
            } else {
                alarmDao.updateAlarm(updated)
                AlarmScheduler.cancel(context, updated)
            }
        }
    }

    fun deleteAlarm(context: Context, alarm: Alarm) {
        viewModelScope.launch {
            AlarmScheduler.cancel(context, alarm)
            alarmDao.deleteAlarm(alarm)
        }
    }

    private fun showScheduledToast(context: Context, triggerAtMillis: Long) {
        val diffMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val hours = diffMs / (3600 * 1000)
        val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)

        val hoursText = if (hours > 0) {
            context.resources.getQuantityString(
                com.example.smartalarmer.R.plurals.hours_plural,
                hours.toInt(),
                hours.toInt()
            )
        } else {
            ""
        }
        val minutesText = context.resources.getQuantityString(
            com.example.smartalarmer.R.plurals.minutes_plural,
            minutes.toInt(),
            minutes.toInt()
        )
        val timeText = if (hours > 0) {
            context.getString(
                com.example.smartalarmer.R.string.hours_and_minutes_connector,
                hoursText,
                minutesText
            )
        } else {
            minutesText
        }

        val message = context.getString(com.example.smartalarmer.R.string.alarm_set_toast, timeText)
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showToast(context: Context, messageRes: Int) {
        android.widget.Toast.makeText(context, messageRes, android.widget.Toast.LENGTH_LONG).show()
    }
}
