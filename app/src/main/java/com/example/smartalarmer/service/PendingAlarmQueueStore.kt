package com.example.smartalarmer.service

import android.annotation.SuppressLint
import android.content.Context
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmPayloadJson
import com.example.smartalarmer.alarm.sessionIdentity
import org.json.JSONArray

internal class PendingAlarmQueueStore(
    context: Context
) {
    private val preferences =
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun enqueue(payload: AlarmLaunchPayload): Boolean = synchronized(LOCK) {
        val current = items()
        if (current.any { it.sessionIdentity == payload.sessionIdentity }) {
            false
        } else {
            write(current + payload)
            true
        }
    }

    fun peek(): AlarmLaunchPayload? = synchronized(LOCK) {
        items().firstOrNull()
    }

    fun removeHead(payload: AlarmLaunchPayload): Boolean = synchronized(LOCK) {
        val current = items()
        if (current.firstOrNull()?.sessionIdentity != payload.sessionIdentity) {
            false
        } else {
            write(current.drop(1))
            true
        }
    }

    fun hasPending(): Boolean = synchronized(LOCK) {
        items().isNotEmpty()
    }

    fun clear() = synchronized(LOCK) {
        write(emptyList())
    }

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

    companion object {
        internal const val PREFERENCES_NAME = "pending_alarm_queue"
        private const val KEY_QUEUE = "queue"
        private val LOCK = Any()
    }
}
