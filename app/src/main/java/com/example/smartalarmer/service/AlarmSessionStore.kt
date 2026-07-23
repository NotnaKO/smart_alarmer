package com.example.smartalarmer.service

import android.annotation.SuppressLint
import android.content.Context
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType

internal data class AlarmAudioSession(
    val payload: AlarmLaunchPayload,
    val originalVolume: Int,
    val activeTaskIndex: Int,
    val dismissRequested: Boolean = false
) {
    val alarmId: Int
        get() = payload.alarmId
}

internal class AlarmSessionStore(
    context: Context
) {
    private val preferences =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun begin(
        payload: AlarmLaunchPayload,
        currentVolume: Int
    ): AlarmAudioSession {
        val existing = current()
        val session =
            AlarmAudioSession(
                payload = payload,
                originalVolume = existing?.originalVolume ?: currentVolume,
                activeTaskIndex = existing?.activeTaskIndex?.takeIf { existing.alarmId == payload.alarmId } ?: 0,
                dismissRequested = false
            )
        check(write(session)) { "Unable to persist the active alarm session" }
        return session
    }

    fun current(): AlarmAudioSession? {
        val originalVolume = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        if (originalVolume == NO_VOLUME) return null
        return AlarmAudioSession(
            payload =
            AlarmLaunchPayload(
                alarmId = preferences.getInt(KEY_ALARM_ID, AlarmLaunchPayload.NO_ALARM_ID),
                puzzlesList = preferences.getString(KEY_PUZZLES_LIST, null).orEmpty(),
                puzzleCount = preferences.getInt(KEY_PUZZLE_COUNT, 1),
                soundUri = preferences.getString(KEY_SOUND_URI, null),
                alarmLabel = preferences.getString(KEY_ALARM_LABEL, null).orEmpty(),
                volumeRampSeconds = preferences.getInt(KEY_VOLUME_RAMP_SECONDS, 60),
                isPreview = false,
                launchType =
                runCatching {
                    AlarmLaunchType.valueOf(preferences.getString(KEY_LAUNCH_TYPE, null).orEmpty())
                }.getOrDefault(AlarmLaunchType.MAIN),
                wakeUpCheckNumber = preferences.getInt(KEY_WAKE_UP_CHECK_NUMBER, 0),
                wakeUpCheckTotal = preferences.getInt(KEY_WAKE_UP_CHECK_TOTAL, 0),
                wakeUpCheckToken = preferences.getString(KEY_WAKE_UP_CHECK_TOKEN, null).orEmpty(),
                wakeUpChecksEnabled = preferences.getBoolean(KEY_WAKE_UP_CHECKS_ENABLED, false),
                wakeUpCheckIntervalMinutes = preferences.getInt(KEY_WAKE_UP_CHECK_INTERVAL_MINUTES, 5),
                occurrenceTriggerAtMillis =
                preferences
                    .getLong(KEY_OCCURRENCE_TRIGGER_AT_MILLIS, AlarmLaunchPayload.NO_OCCURRENCE)
                    .coerceAtLeast(AlarmLaunchPayload.NO_OCCURRENCE)
            ),
            originalVolume = originalVolume,
            activeTaskIndex = preferences.getInt(KEY_ACTIVE_TASK_INDEX, 0).coerceAtLeast(0),
            dismissRequested = preferences.getBoolean(KEY_DISMISS_REQUESTED, false)
        )
    }

    fun updateActiveTaskIndex(
        alarmId: Int,
        taskIndex: Int
    ) {
        val session = current()?.takeIf { it.alarmId == alarmId } ?: return
        check(write(session.copy(activeTaskIndex = taskIndex.coerceAtLeast(0)))) {
            "Unable to persist active alarm progress"
        }
    }

    fun markDismissRequested(alarmId: Int) {
        val session = current()?.takeIf { it.alarmId == alarmId } ?: return
        check(write(session.copy(dismissRequested = true))) {
            "Unable to persist the alarm dismissal request"
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear the active alarm session" }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun write(session: AlarmAudioSession): Boolean = preferences
        .edit()
        .putInt(KEY_ALARM_ID, session.payload.alarmId)
        .putInt(KEY_ORIGINAL_VOLUME, session.originalVolume)
        .putString(KEY_PUZZLES_LIST, session.payload.puzzlesList)
        .putInt(KEY_PUZZLE_COUNT, session.payload.puzzleCount)
        .putString(KEY_SOUND_URI, session.payload.soundUri)
        .putString(KEY_ALARM_LABEL, session.payload.alarmLabel)
        .putInt(KEY_VOLUME_RAMP_SECONDS, session.payload.volumeRampSeconds)
        .putInt(KEY_ACTIVE_TASK_INDEX, session.activeTaskIndex)
        .putBoolean(KEY_DISMISS_REQUESTED, session.dismissRequested)
        .putString(KEY_LAUNCH_TYPE, session.payload.launchType.name)
        .putInt(KEY_WAKE_UP_CHECK_NUMBER, session.payload.wakeUpCheckNumber)
        .putInt(KEY_WAKE_UP_CHECK_TOTAL, session.payload.wakeUpCheckTotal)
        .putString(KEY_WAKE_UP_CHECK_TOKEN, session.payload.wakeUpCheckToken)
        .putBoolean(KEY_WAKE_UP_CHECKS_ENABLED, session.payload.wakeUpChecksEnabled)
        .putInt(KEY_WAKE_UP_CHECK_INTERVAL_MINUTES, session.payload.wakeUpCheckIntervalMinutes)
        .putLong(KEY_OCCURRENCE_TRIGGER_AT_MILLIS, session.payload.occurrenceTriggerAtMillis)
        .commit()

    companion object {
        internal const val PREFERENCES_NAME = "active_alarm_session"
        private const val KEY_ALARM_ID = "alarm_id"
        private const val KEY_ORIGINAL_VOLUME = "original_volume"
        private const val KEY_PUZZLES_LIST = "puzzles_list"
        private const val KEY_PUZZLE_COUNT = "puzzle_count"
        private const val KEY_SOUND_URI = "sound_uri"
        private const val KEY_ALARM_LABEL = "alarm_label"
        private const val KEY_VOLUME_RAMP_SECONDS = "volume_ramp_seconds"
        private const val KEY_ACTIVE_TASK_INDEX = "active_task_index"
        private const val KEY_DISMISS_REQUESTED = "dismiss_requested"
        private const val KEY_LAUNCH_TYPE = "launch_type"
        private const val KEY_WAKE_UP_CHECK_NUMBER = "wake_up_check_number"
        private const val KEY_WAKE_UP_CHECK_TOTAL = "wake_up_check_total"
        private const val KEY_WAKE_UP_CHECK_TOKEN = "wake_up_check_token"
        private const val KEY_WAKE_UP_CHECKS_ENABLED = "wake_up_checks_enabled"
        private const val KEY_WAKE_UP_CHECK_INTERVAL_MINUTES = "wake_up_check_interval_minutes"
        private const val KEY_OCCURRENCE_TRIGGER_AT_MILLIS = "occurrence_trigger_at_millis"
        private const val NO_VOLUME = -1
    }
}
