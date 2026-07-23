package com.example.smartalarmer.alarm

import com.example.smartalarmer.domain.AlarmVolumeRamp
import com.example.smartalarmer.domain.BackupAlarmConfig
import org.json.JSONObject

internal object AlarmPayloadJson {
    fun encode(payload: AlarmLaunchPayload): JSONObject = JSONObject()
        .put("alarmId", payload.alarmId)
        .put("puzzlesList", payload.puzzlesList)
        .put("puzzleCount", payload.puzzleCount)
        .put("soundUri", payload.soundUri ?: JSONObject.NULL)
        .put("alarmLabel", payload.alarmLabel)
        .put("volumeRampSeconds", payload.volumeRampSeconds)
        .put("isPreview", payload.isPreview)
        .put("launchType", payload.launchType.name)
        .put("wakeUpCheckNumber", payload.wakeUpCheckNumber)
        .put("wakeUpCheckTotal", payload.wakeUpCheckTotal)
        .put("wakeUpCheckToken", payload.wakeUpCheckToken)
        .put("wakeUpChecksEnabled", payload.wakeUpChecksEnabled)
        .put("wakeUpCheckIntervalMinutes", payload.wakeUpCheckIntervalMinutes)
        .put("backupAlarmTimeoutMinutes", payload.backupAlarmTimeoutMinutes)
        .put("backupAlarmRepeatCount", payload.backupAlarmRepeatCount)

    fun decode(json: JSONObject): AlarmLaunchPayload = AlarmLaunchPayload(
        alarmId = json.optInt("alarmId", AlarmLaunchPayload.NO_ALARM_ID),
        puzzlesList = json.optString("puzzlesList"),
        puzzleCount = json.optInt("puzzleCount", 1),
        soundUri = json.optNullableString("soundUri"),
        alarmLabel = json.optString("alarmLabel"),
        volumeRampSeconds =
        AlarmVolumeRamp.sanitize(
            json.optInt("volumeRampSeconds", AlarmVolumeRamp.DEFAULT_SECONDS)
        ),
        isPreview = json.optBoolean("isPreview", false),
        launchType =
        runCatching {
            AlarmLaunchType.valueOf(json.optString("launchType"))
        }.getOrDefault(AlarmLaunchType.MAIN),
        wakeUpCheckNumber = json.optInt("wakeUpCheckNumber", 0).coerceAtLeast(0),
        wakeUpCheckTotal = json.optInt("wakeUpCheckTotal", 0).coerceAtLeast(0),
        wakeUpCheckToken = json.optString("wakeUpCheckToken"),
        wakeUpChecksEnabled = json.optBoolean("wakeUpChecksEnabled", false),
        wakeUpCheckIntervalMinutes = json.optInt("wakeUpCheckIntervalMinutes", 5).coerceAtLeast(1),
        backupAlarmTimeoutMinutes =
        json
            .optInt("backupAlarmTimeoutMinutes", BackupAlarmConfig.DEFAULT_TIMEOUT_MINUTES)
            .takeIf { it in BackupAlarmConfig.TIMEOUT_OPTIONS_MINUTES }
            ?: BackupAlarmConfig.DEFAULT_TIMEOUT_MINUTES,
        backupAlarmRepeatCount =
        json
            .optInt("backupAlarmRepeatCount", BackupAlarmConfig.DEFAULT_REPEAT_COUNT)
            .coerceIn(BackupAlarmConfig.REPEAT_COUNT_RANGE)
    )

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
}
