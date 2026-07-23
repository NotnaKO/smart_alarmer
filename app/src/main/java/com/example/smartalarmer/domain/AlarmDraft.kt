package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmScheduleStatus

data class AlarmDraft(
    val hour: Int,
    val minute: Int,
    val repeatDays: AlarmDays,
    val repeatWeekParity: AlarmWeekParity = AlarmWeekParity.EVERY,
    val puzzleSelection: PuzzleSelection,
    val puzzleCount: Int,
    val label: String,
    val soundUri: String?,
    val wakeUpChecksEnabled: Boolean = false,
    val wakeUpCheckCount: Int = WakeUpCheckConfig.DEFAULT_COUNT,
    val wakeUpCheckIntervalMinutes: Int = WakeUpCheckConfig.DEFAULT_INTERVAL_MINUTES,
    val volumeRampSeconds: Int = AlarmVolumeRamp.DEFAULT_SECONDS
) {
    init {
        require(hour in 0..23) { "Alarm hour must be between 0 and 23" }
        require(minute in 0..59) { "Alarm minute must be between 0 and 59" }
        require(puzzleCount in 1..puzzleSelection.values.size) {
            "Puzzle count must match the selected puzzles"
        }
        require(volumeRampSeconds in AlarmVolumeRamp.OPTIONS_SECONDS) {
            "Volume ramp duration must be a supported preset"
        }
        require(wakeUpCheckCount in WakeUpCheckConfig.COUNT_RANGE) {
            "Wake-up check count must be supported"
        }
        require(wakeUpCheckIntervalMinutes in WakeUpCheckConfig.INTERVAL_OPTIONS_MINUTES) {
            "Wake-up check interval must be a supported preset"
        }
    }

    fun toAlarm(
        existing: Alarm? = null,
        isEnabled: Boolean
    ): Alarm = Alarm(
        id = existing?.id ?: 0,
        hour = hour,
        minute = minute,
        daysOfWeek = repeatDays.encoded,
        weekParity = if (repeatDays.isOneTime) AlarmWeekParity.EVERY.name else repeatWeekParity.name,
        isEnabled = isEnabled,
        puzzlesList = puzzleSelection.encoded,
        puzzleCount = puzzleCount,
        volumeRampSeconds = volumeRampSeconds,
        label = label.trim(),
        soundUri = soundUri,
        wakeUpChecksEnabled = wakeUpChecksEnabled,
        wakeUpCheckCount = wakeUpCheckCount,
        wakeUpCheckIntervalMinutes = wakeUpCheckIntervalMinutes,
        backupAlarmTimeoutMinutes = BackupAlarmConfig.DEFAULT_TIMEOUT_MINUTES,
        backupAlarmRepeatCount = BackupAlarmConfig.DEFAULT_REPEAT_COUNT,
        scheduleStatus = if (isEnabled) AlarmScheduleStatus.UNKNOWN.name else AlarmScheduleStatus.DISABLED.name,
        scheduledTriggerAtMillis = null
    )
}

object WakeUpCheckConfig {
    const val DEFAULT_COUNT = 3
    const val DEFAULT_INTERVAL_MINUTES = 5
    val COUNT_RANGE = 1..5
    val INTERVAL_OPTIONS_MINUTES = listOf(5, 10, 15)
}

object BackupAlarmConfig {
    const val DEFAULT_TIMEOUT_MINUTES = 10
    const val DEFAULT_REPEAT_COUNT = 3
    val TIMEOUT_OPTIONS_MINUTES = listOf(5, 10, 15)
    val REPEAT_COUNT_RANGE = 1..3
}
