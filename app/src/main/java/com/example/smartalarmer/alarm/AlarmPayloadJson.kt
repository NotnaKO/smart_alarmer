package com.example.smartalarmer.alarm

import com.example.smartalarmer.domain.AlarmVolumeRamp
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
        .put("occurrenceTriggerAtMillis", payload.occurrenceTriggerAtMillis)

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
        occurrenceTriggerAtMillis =
        json
            .optLong("occurrenceTriggerAtMillis", AlarmLaunchPayload.NO_OCCURRENCE)
            .coerceAtLeast(AlarmLaunchPayload.NO_OCCURRENCE)
    )

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
}
