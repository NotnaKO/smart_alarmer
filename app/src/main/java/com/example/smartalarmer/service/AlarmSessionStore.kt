package com.example.smartalarmer.service

import android.content.Context

internal data class AlarmAudioSession(
    val alarmId: Int,
    val originalVolume: Int
)

internal class AlarmSessionStore(
    context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun begin(
        alarmId: Int,
        currentVolume: Int
    ): AlarmAudioSession {
        val storedVolume = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        val originalVolume = if (storedVolume == NO_VOLUME) currentVolume else storedVolume
        preferences
            .edit()
            .putInt(KEY_ALARM_ID, alarmId)
            .putInt(KEY_ORIGINAL_VOLUME, originalVolume)
            .apply()
        return AlarmAudioSession(alarmId, originalVolume)
    }

    fun current(): AlarmAudioSession? {
        val originalVolume = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        if (originalVolume == NO_VOLUME) return null
        return AlarmAudioSession(
            alarmId = preferences.getInt(KEY_ALARM_ID, AlarmLaunchPayloadDefaults.NO_ALARM_ID),
            originalVolume = originalVolume
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private object AlarmLaunchPayloadDefaults {
        const val NO_ALARM_ID = -1
    }

    companion object {
        private const val PREFERENCES_NAME = "active_alarm_session"
        private const val KEY_ALARM_ID = "alarm_id"
        private const val KEY_ORIGINAL_VOLUME = "original_volume"
        private const val NO_VOLUME = -1
    }
}
