package com.example.smartalarmer.data

import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    val alarms: Flow<List<Alarm>>

    suspend fun getEnabledAlarms(): List<Alarm>
    suspend fun getAlarmById(id: Int): Alarm?
    suspend fun insertAlarm(alarm: Alarm): Alarm
    suspend fun updateAlarm(alarm: Alarm)
    suspend fun deleteAlarm(alarm: Alarm)
}

class RoomAlarmRepository(private val alarmDao: AlarmDao) : AlarmRepository {
    override val alarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    override suspend fun getEnabledAlarms(): List<Alarm> = alarmDao.getEnabledAlarms()

    override suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)

    override suspend fun insertAlarm(alarm: Alarm): Alarm {
        val generatedId = alarmDao.insertAlarm(alarm).toInt()
        return alarm.copy(id = generatedId)
    }

    override suspend fun updateAlarm(alarm: Alarm) = alarmDao.updateAlarm(alarm)

    override suspend fun deleteAlarm(alarm: Alarm) = alarmDao.deleteAlarm(alarm)
}
