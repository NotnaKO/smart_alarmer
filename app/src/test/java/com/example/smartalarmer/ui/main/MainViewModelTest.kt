package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    private var insertCalled = false
    private var updateCalled = false
    private var deleteCalled = false

    private val fakeDao = object : AlarmDao {
        val list = mutableListOf<Alarm>()
        override fun getAllAlarms(): Flow<List<Alarm>> = flowOf(list)
        override suspend fun getEnabledAlarms(): List<Alarm> = list.filter { it.isEnabled }
        override suspend fun getAlarmById(id: Int): Alarm? = list.find { it.id == id }
        override suspend fun insertAlarm(alarm: Alarm): Long {
            list.add(alarm)
            insertCalled = true
            return list.size.toLong()
        }
        override suspend fun updateAlarm(alarm: Alarm) {
            val idx = list.indexOfFirst { it.id == alarm.id }
            if (idx != -1) list[idx] = alarm
            updateCalled = true
        }
        override suspend fun deleteAlarm(alarm: Alarm) {
            list.removeIf { it.id == alarm.id }
            deleteCalled = true
        }
    }

    @Test
    fun testInitialState() {
        val viewModel = MainViewModel(fakeDao)
        assertFalse(viewModel.isBottomSheetVisible.value)
        assertEquals(null, viewModel.editingAlarm.value)
    }
}
