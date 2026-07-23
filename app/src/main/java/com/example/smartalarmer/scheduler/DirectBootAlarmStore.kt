package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.content.Context
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmScheduleStatus
import org.json.JSONArray
import org.json.JSONObject

internal data class DirectBootAlarmSnapshot(
    val alarm: Alarm,
    val triggerAtMillis: Long
)

/**
 * Mirrors only the information Android needs before credential-encrypted Room storage is
 * available. This is operational state, not a user backup.
 */
internal class DirectBootAlarmStore(
    context: Context
) {
    private val preferences =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun snapshots(): List<DirectBootAlarmSnapshot> = decodeSnapshots(
        preferences.getString(KEY_SNAPSHOTS, null)
    )

    @Synchronized
    fun upsert(
        alarm: Alarm,
        triggerAtMillis: Long
    ) {
        val updated =
            snapshots()
                .filterNot { it.alarm.id == alarm.id }
                .plus(DirectBootAlarmSnapshot(alarm, triggerAtMillis))
                .sortedBy { it.alarm.id }
        writeSnapshots(updated)
    }

    @Synchronized
    fun remove(alarmId: Int) {
        writeSnapshots(snapshots().filterNot { it.alarm.id == alarmId })
    }

    @Synchronized
    fun retainAlarmIds(alarmIds: Set<Int>) {
        writeSnapshots(snapshots().filter { it.alarm.id in alarmIds })
    }

    @Synchronized
    fun markDeliveryForUnlock(alarmId: Int) {
        preferences
            .edit()
            .putStringSet(
                KEY_DELIVERED_IDS,
                preferences.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty() + alarmId.toString()
            ).commitOrThrow()
    }

    @Synchronized
    fun markDismissalForUnlock(alarmId: Int) {
        preferences
            .edit()
            .putStringSet(
                KEY_DISMISSED_IDS,
                preferences.getStringSet(KEY_DISMISSED_IDS, emptySet()).orEmpty() + alarmId.toString()
            ).commitOrThrow()
    }

    @Synchronized
    fun deliveredAlarmIds(): Set<Int> = readIds(KEY_DELIVERED_IDS)

    @Synchronized
    fun dismissedAlarmIds(): Set<Int> = readIds(KEY_DISMISSED_IDS)

    @Synchronized
    fun clearDeliveredAlarmId(alarmId: Int) = clearId(KEY_DELIVERED_IDS, alarmId)

    @Synchronized
    fun clearDismissedAlarmId(alarmId: Int) = clearId(KEY_DISMISSED_IDS, alarmId)

    @SuppressLint("ApplySharedPref")
    private fun writeSnapshots(snapshots: List<DirectBootAlarmSnapshot>) {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(
                JSONObject()
                    .put("triggerAtMillis", snapshot.triggerAtMillis)
                    .put("alarm", encodeAlarm(snapshot.alarm))
            )
        }
        preferences.edit().putString(KEY_SNAPSHOTS, array.toString()).commitOrThrow()
    }

    private fun decodeSnapshots(encoded: String?): List<DirectBootAlarmSnapshot> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        DirectBootAlarmSnapshot(
                            alarm = decodeAlarm(item.getJSONObject("alarm")),
                            triggerAtMillis = item.getLong("triggerAtMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeAlarm(alarm: Alarm): JSONObject = JSONObject()
        .put("id", alarm.id)
        .put("hour", alarm.hour)
        .put("minute", alarm.minute)
        .put("daysOfWeek", alarm.daysOfWeek)
        .put("weekParity", alarm.weekParity)
        .put("isEnabled", alarm.isEnabled)
        .put("puzzlesList", alarm.puzzlesList)
        .put("puzzleCount", alarm.puzzleCount)
        .put("volumeRampSeconds", alarm.volumeRampSeconds)
        .put("label", alarm.label)
        .put("soundUri", alarm.soundUri ?: JSONObject.NULL)
        .put("wakeUpChecksEnabled", alarm.wakeUpChecksEnabled)
        .put("wakeUpCheckCount", alarm.wakeUpCheckCount)
        .put("wakeUpCheckIntervalMinutes", alarm.wakeUpCheckIntervalMinutes)
        .put("scheduleStatus", alarm.scheduleStatus)
        .put("scheduledTriggerAtMillis", alarm.scheduledTriggerAtMillis ?: JSONObject.NULL)

    private fun decodeAlarm(json: JSONObject): Alarm = Alarm(
        id = json.getInt("id"),
        hour = json.getInt("hour"),
        minute = json.getInt("minute"),
        daysOfWeek = json.optString("daysOfWeek"),
        weekParity = json.optString("weekParity", "EVERY"),
        isEnabled = json.optBoolean("isEnabled", true),
        puzzlesList = json.optString("puzzlesList", "MATH"),
        puzzleCount = json.optInt("puzzleCount", 1),
        volumeRampSeconds = json.optInt("volumeRampSeconds", 60),
        label = json.optString("label"),
        soundUri = if (json.isNull("soundUri")) null else json.optString("soundUri"),
        wakeUpChecksEnabled = json.optBoolean("wakeUpChecksEnabled", false),
        wakeUpCheckCount = json.optInt("wakeUpCheckCount", 3),
        wakeUpCheckIntervalMinutes = json.optInt("wakeUpCheckIntervalMinutes", 5),
        scheduleStatus = json.optString("scheduleStatus", AlarmScheduleStatus.UNKNOWN.name),
        scheduledTriggerAtMillis =
        if (json.isNull("scheduledTriggerAtMillis")) {
            null
        } else {
            json.optLong("scheduledTriggerAtMillis")
        }
    )

    private fun readIds(key: String): Set<Int> = preferences
        .getStringSet(key, emptySet())
        .orEmpty()
        .mapNotNull(String::toIntOrNull)
        .toSet()

    @SuppressLint("ApplySharedPref")
    private fun clearId(
        key: String,
        alarmId: Int
    ) {
        preferences
            .edit()
            .putStringSet(
                key,
                preferences.getStringSet(key, emptySet()).orEmpty() - alarmId.toString()
            ).commitOrThrow()
    }

    private fun android.content.SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "Unable to persist direct-boot alarm state" }
    }

    companion object {
        internal const val PREFERENCES_NAME = "direct_boot_alarms"
        private const val KEY_SNAPSHOTS = "snapshots"
        private const val KEY_DELIVERED_IDS = "delivered_alarm_ids"
        private const val KEY_DISMISSED_IDS = "dismissed_alarm_ids"
    }
}
