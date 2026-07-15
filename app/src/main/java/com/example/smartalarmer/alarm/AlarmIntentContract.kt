package com.example.smartalarmer.alarm

import android.content.Intent
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.AlarmVolumeRamp
import com.example.smartalarmer.domain.PuzzleSelection

data class AlarmLaunchPayload(
    val alarmId: Int = NO_ALARM_ID,
    val puzzlesList: String = PuzzleSelection.DEFAULT.encoded,
    val puzzleCount: Int = 1,
    val soundUri: String? = null,
    val alarmLabel: String = "",
    val volumeRampSeconds: Int = AlarmVolumeRamp.DEFAULT_SECONDS,
    val isPreview: Boolean = false
) {
    companion object {
        const val NO_ALARM_ID = -1

        fun fromAlarm(
            alarm: Alarm,
            isPreview: Boolean = false
        ): AlarmLaunchPayload {
            val puzzles = PuzzleSelection.parse(alarm.puzzlesList)
            return AlarmLaunchPayload(
                alarmId = alarm.id,
                puzzlesList = puzzles.encoded,
                puzzleCount = alarm.puzzleCount.coerceIn(1, puzzles.values.size),
                soundUri = alarm.soundUri,
                alarmLabel = alarm.label,
                volumeRampSeconds = AlarmVolumeRamp.sanitize(alarm.volumeRampSeconds),
                isPreview = isPreview
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
            isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        )
    }
}
