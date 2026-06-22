package com.example.smartalarmer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartalarmer.AlarmScheduler
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

class MainViewModel(private val alarmDao: AlarmDao) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
        .let { flow ->
            val state = MutableStateFlow<List<Alarm>>(emptyList())
            viewModelScope.launch {
                flow.collect { state.value = it }
            }
            state.asStateFlow()
        }

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
        isGradualVolume: Boolean
    ) {
        viewModelScope.launch {
            val current = _editingAlarm.value
            if (current != null) {
                val updated = current.copy(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    isEnabled = true
                )
                alarmDao.updateAlarm(updated)
                AlarmScheduler.schedule(context, updated)
            } else {
                val newAlarm = Alarm(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    isEnabled = true
                )
                val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                val scheduled = newAlarm.copy(id = generatedId)
                AlarmScheduler.schedule(context, scheduled)
            }
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
