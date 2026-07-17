package com.example.smartalarmer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // CSV e.g., "1,2,3,4,5"
    val isEnabled: Boolean = true,
    val puzzlesList: String, // CSV e.g., "MATH,TYPING,MEMORY"
    val puzzleCount: Int = 2,
    val isGradualVolume: Boolean = true,
    val volumeRampSeconds: Int = 60,
    val label: String = "",
    val soundUri: String? = null,
    val wakeUpChecksEnabled: Boolean = false,
    val wakeUpCheckCount: Int = 3,
    val wakeUpCheckIntervalMinutes: Int = 5,
    val scheduleStatus: String = AlarmScheduleStatus.UNKNOWN.name,
    val scheduledTriggerAtMillis: Long? = null
)

@Entity(tableName = "wake_up_check_sessions")
data class WakeUpCheckSession(
    @PrimaryKey val alarmId: Int,
    val token: String,
    val nextCheckNumber: Int,
    val totalChecks: Int,
    val intervalMinutes: Int,
    val nextTriggerAtMillis: Long,
    val puzzlesList: String,
    val soundUri: String?,
    val alarmLabel: String
)

enum class AlarmScheduleStatus {
    DISABLED,
    SCHEDULED,
    PERMISSION_REQUIRED,
    FAILED,
    UNKNOWN
    ;

    companion object {
        fun parse(value: String?): AlarmScheduleStatus = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

val Alarm.scheduleHealth: AlarmScheduleStatus
    get() = AlarmScheduleStatus.parse(scheduleStatus)
