package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmScheduleStatus

data class AlarmDraft(
    val hour: Int,
    val minute: Int,
    val repeatDays: AlarmDays,
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
        isEnabled = isEnabled,
        puzzlesList = puzzleSelection.encoded,
        puzzleCount = puzzleCount,
        isGradualVolume = true,
        volumeRampSeconds = volumeRampSeconds,
        label = label.trim(),
        soundUri = soundUri,
        wakeUpChecksEnabled = wakeUpChecksEnabled,
        wakeUpCheckCount = wakeUpCheckCount,
        wakeUpCheckIntervalMinutes = wakeUpCheckIntervalMinutes,
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
