package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            val scheduledAlarm = if (current != null) {
                val updated = current.copy(
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
                alarmDao.updateAlarm(updated)
                AlarmScheduler.schedule(context, updated)
                updated
            } else {
                val newAlarm = Alarm(
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
                val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                val scheduled = newAlarm.copy(id = generatedId)
                AlarmScheduler.schedule(context, scheduled)
                scheduled
            }

            // Show Toast with remaining time
            val nextTrigger = AlarmScheduler.calculateNextTriggerTime(scheduledAlarm, java.util.Calendar.getInstance())
            val diffMs = nextTrigger.timeInMillis - System.currentTimeMillis()
            val hours = diffMs / (3600 * 1000)
            val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)
            
            val hoursText = if (hours > 0) {
                context.resources.getQuantityString(com.example.smartalarmer.R.plurals.hours_plural, hours.toInt(), hours.toInt())
            } else ""
            
            val minutesText = context.resources.getQuantityString(com.example.smartalarmer.R.plurals.minutes_plural, minutes.toInt(), minutes.toInt())
            
            val timeText = if (hours > 0) {
                context.getString(com.example.smartalarmer.R.string.hours_and_minutes_connector, hoursText, minutesText)
            } else {
                minutesText
            }
            
            val toastMsg = context.getString(com.example.smartalarmer.R.string.alarm_set_toast, timeText)
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_LONG).show()

            closeEditSheet()
        }
    }

    fun toggleAlarm(context: Context, alarm: Alarm, isChecked: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isChecked)
            alarmDao.updateAlarm(updated)
            if (isChecked) {
                AlarmScheduler.schedule(context, updated)
            } else {
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
}
