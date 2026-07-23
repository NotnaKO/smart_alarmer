package com.example.smartalarmer.alarm

import android.content.Intent
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.AlarmVolumeRamp
import com.example.smartalarmer.domain.PuzzleSelection

enum class AlarmLaunchType {
    MAIN,
    WAKE_UP_CHECK
}

data class AlarmLaunchPayload(
    val alarmId: Int = NO_ALARM_ID,
    val puzzlesList: String = PuzzleSelection.DEFAULT.encoded,
    val puzzleCount: Int = 1,
    val soundUri: String? = null,
    val alarmLabel: String = "",
    val volumeRampSeconds: Int = AlarmVolumeRamp.DEFAULT_SECONDS,
    val isPreview: Boolean = false,
    val launchType: AlarmLaunchType = AlarmLaunchType.MAIN,
    val wakeUpCheckNumber: Int = 0,
    val wakeUpCheckTotal: Int = 0,
    val wakeUpCheckToken: String = "",
    val wakeUpChecksEnabled: Boolean = false,
    val wakeUpCheckIntervalMinutes: Int = 5,
    val occurrenceTriggerAtMillis: Long = NO_OCCURRENCE
) {
    companion object {
        const val NO_ALARM_ID = -1
        const val NO_OCCURRENCE = 0L

        fun fromAlarm(
            alarm: Alarm,
            isPreview: Boolean = false,
            occurrenceTriggerAtMillis: Long = NO_OCCURRENCE
        ): AlarmLaunchPayload {
            val puzzles = PuzzleSelection.parse(alarm.puzzlesList)
            return AlarmLaunchPayload(
                alarmId = alarm.id,
                puzzlesList = puzzles.encoded,
                puzzleCount = alarm.puzzleCount.coerceIn(1, puzzles.values.size),
                soundUri = alarm.soundUri,
                alarmLabel = alarm.label,
                volumeRampSeconds = AlarmVolumeRamp.sanitize(alarm.volumeRampSeconds),
                isPreview = isPreview,
                wakeUpChecksEnabled = alarm.wakeUpChecksEnabled,
                wakeUpCheckIntervalMinutes = alarm.wakeUpCheckIntervalMinutes,
                occurrenceTriggerAtMillis = occurrenceTriggerAtMillis.coerceAtLeast(NO_OCCURRENCE)
            )
        }
    }
}

object AlarmIntentContract {
    const val EXTRA_ALARM_ID = "ALARM_ID"
    const val EXTRA_PUZZLES_LIST = "PUZZLES_LIST"
    const val EXTRA_PUZZLE_COUNT = "PUZZLE_COUNT"
    const val EXTRA_SOUND_URI = "SOUND_URI"
    const val EXTRA_ALARM_LABEL = "ALARM_LABEL"
    const val EXTRA_VOLUME_RAMP_SECONDS = "VOLUME_RAMP_SECONDS"
    const val EXTRA_IS_PREVIEW = "IS_PREVIEW"
    const val EXTRA_LAUNCH_TYPE = "LAUNCH_TYPE"
    const val EXTRA_WAKE_UP_CHECK_NUMBER = "WAKE_UP_CHECK_NUMBER"
    const val EXTRA_WAKE_UP_CHECK_TOTAL = "WAKE_UP_CHECK_TOTAL"
    const val EXTRA_WAKE_UP_CHECK_TOKEN = "WAKE_UP_CHECK_TOKEN"
    const val EXTRA_WAKE_UP_CHECKS_ENABLED = "WAKE_UP_CHECKS_ENABLED"
    const val EXTRA_WAKE_UP_CHECK_INTERVAL_MINUTES = "WAKE_UP_CHECK_INTERVAL_MINUTES"
    const val EXTRA_OCCURRENCE_TRIGGER_AT_MILLIS = "OCCURRENCE_TRIGGER_AT_MILLIS"

    fun write(
        intent: Intent,
        payload: AlarmLaunchPayload
    ): Intent = intent.apply {
        putExtra(EXTRA_ALARM_ID, payload.alarmId)
        putExtra(EXTRA_PUZZLES_LIST, payload.puzzlesList)
        putExtra(EXTRA_PUZZLE_COUNT, payload.puzzleCount)
        putExtra(EXTRA_SOUND_URI, payload.soundUri)
        putExtra(EXTRA_ALARM_LABEL, payload.alarmLabel)
        putExtra(EXTRA_VOLUME_RAMP_SECONDS, payload.volumeRampSeconds)
        putExtra(EXTRA_IS_PREVIEW, payload.isPreview)
        putExtra(EXTRA_LAUNCH_TYPE, payload.launchType.name)
        putExtra(EXTRA_WAKE_UP_CHECK_NUMBER, payload.wakeUpCheckNumber)
        putExtra(EXTRA_WAKE_UP_CHECK_TOTAL, payload.wakeUpCheckTotal)
        putExtra(EXTRA_WAKE_UP_CHECK_TOKEN, payload.wakeUpCheckToken)
        putExtra(EXTRA_WAKE_UP_CHECKS_ENABLED, payload.wakeUpChecksEnabled)
        putExtra(EXTRA_WAKE_UP_CHECK_INTERVAL_MINUTES, payload.wakeUpCheckIntervalMinutes)
        putExtra(EXTRA_OCCURRENCE_TRIGGER_AT_MILLIS, payload.occurrenceTriggerAtMillis)
    }

    fun read(intent: Intent): AlarmLaunchPayload {
        val puzzles = PuzzleSelection.parse(intent.getStringExtra(EXTRA_PUZZLES_LIST))
        return AlarmLaunchPayload(
            alarmId = intent.getIntExtra(EXTRA_ALARM_ID, AlarmLaunchPayload.NO_ALARM_ID),
            puzzlesList = puzzles.encoded,
            puzzleCount =
            intent
                .getIntExtra(EXTRA_PUZZLE_COUNT, 1)
                .coerceIn(1, puzzles.values.size),
            soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
            alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty(),
            volumeRampSeconds =
            AlarmVolumeRamp.sanitize(
                intent.getIntExtra(EXTRA_VOLUME_RAMP_SECONDS, AlarmVolumeRamp.DEFAULT_SECONDS)
            ),
            isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false),
            launchType =
            runCatching {
                AlarmLaunchType.valueOf(intent.getStringExtra(EXTRA_LAUNCH_TYPE).orEmpty())
            }.getOrDefault(AlarmLaunchType.MAIN),
            wakeUpCheckNumber = intent.getIntExtra(EXTRA_WAKE_UP_CHECK_NUMBER, 0).coerceAtLeast(0),
            wakeUpCheckTotal = intent.getIntExtra(EXTRA_WAKE_UP_CHECK_TOTAL, 0).coerceAtLeast(0),
            wakeUpCheckToken = intent.getStringExtra(EXTRA_WAKE_UP_CHECK_TOKEN).orEmpty(),
            wakeUpChecksEnabled = intent.getBooleanExtra(EXTRA_WAKE_UP_CHECKS_ENABLED, false),
            wakeUpCheckIntervalMinutes =
            intent.getIntExtra(EXTRA_WAKE_UP_CHECK_INTERVAL_MINUTES, 5).coerceAtLeast(1),
            occurrenceTriggerAtMillis =
            intent
                .getLongExtra(EXTRA_OCCURRENCE_TRIGGER_AT_MILLIS, AlarmLaunchPayload.NO_OCCURRENCE)
                .coerceAtLeast(AlarmLaunchPayload.NO_OCCURRENCE)
        )
    }
}

internal val AlarmLaunchPayload.sessionIdentity: String
    get() =
        "$alarmId:${launchType.name}:$occurrenceTriggerAtMillis:" +
            "$wakeUpCheckNumber:$wakeUpCheckToken"
