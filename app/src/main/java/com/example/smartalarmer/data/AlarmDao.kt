package com.example.smartalarmer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)
}

@Dao
interface WakeUpCheckDao {
    @Query("SELECT * FROM wake_up_check_sessions")
    fun observeAllSessions(): Flow<List<WakeUpCheckSession>>

    @Query("SELECT * FROM wake_up_check_sessions WHERE alarmId = :alarmId")
    suspend fun getSession(alarmId: Int): WakeUpCheckSession?

    @Query("SELECT * FROM wake_up_check_sessions")
    suspend fun getAllSessions(): List<WakeUpCheckSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WakeUpCheckSession)

    @Query("DELETE FROM wake_up_check_sessions WHERE alarmId = :alarmId")
    suspend fun deleteSession(alarmId: Int)
}
