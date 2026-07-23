package com.example.smartalarmer.service

import android.annotation.SuppressLint
import android.content.Context
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmPayloadJson
import org.json.JSONArray

internal class PendingAlarmQueueStore(
    context: Context
) {
    private val preferences =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(payload: AlarmLaunchPayload): Boolean {
        val current = items()
        if (current.any { it.sessionKey == payload.sessionKey }) return false
        write(current + payload)
        return true
    }

    @Synchronized
    fun dequeue(): AlarmLaunchPayload? {
        val current = items()
        val first = current.firstOrNull() ?: return null
        write(current.drop(1))
        return first
    }

    @Synchronized
    fun hasPending(): Boolean = items().isNotEmpty()

    @Synchronized
    fun clear() = write(emptyList())

    private fun items(): List<AlarmLaunchPayload> {
        val encoded = preferences.getString(KEY_QUEUE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    add(AlarmPayloadJson.decode(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("ApplySharedPref")
    private fun write(items: List<AlarmLaunchPayload>) {
        val array = JSONArray()
        items.forEach { array.put(AlarmPayloadJson.encode(it)) }
        check(preferences.edit().putString(KEY_QUEUE, array.toString()).commit()) {
            "Unable to persist pending alarms"
        }
    }

    private val AlarmLaunchPayload.sessionKey: String
        get() = "$alarmId:${launchType.name}:$wakeUpCheckNumber:$wakeUpCheckToken"

    companion object {
        internal const val PREFERENCES_NAME = "pending_alarm_queue"
        private const val KEY_QUEUE = "queue"
    }
}
