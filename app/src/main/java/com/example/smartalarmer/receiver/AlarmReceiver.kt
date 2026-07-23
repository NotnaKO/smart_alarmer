package com.example.smartalarmer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val payload = AlarmIntentContract.read(intent)
        if (payload.alarmId == AlarmLaunchPayload.NO_ALARM_ID) {
            startAlarmService(context, payload)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AlarmDatabase.getDatabase(context)
                val alarmDao = database.alarmDao()
                val alarm = alarmDao.getAlarmById(payload.alarmId)
                val shouldDeliver =
                    when (payload.launchType) {
                        AlarmLaunchType.MAIN -> shouldDeliver(alarm)
                        AlarmLaunchType.WAKE_UP_CHECK -> {
                            val session = database.wakeUpCheckDao().getSession(payload.alarmId)
                            session != null &&
                                session.token == payload.wakeUpCheckToken &&
                                session.nextCheckNumber == payload.wakeUpCheckNumber
                        }
                    }
                if (!shouldDeliver || alarm == null) {
                    Log.i(TAG, "Ignoring stale alarm delivery for id ${payload.alarmId}")
                    return@launch
                }

                try {
                    startAlarmService(context, payloadForDelivery(alarm, payload))
                } catch (error: Exception) {
                    alarmDao.updateAlarm(
                        alarm.copy(
                            scheduleStatus = AlarmScheduleStatus.FAILED.name,
                            scheduledTriggerAtMillis = null
                        )
                    )
                    Log.e(TAG, "Unable to start alarm service for ${alarm.id}", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to deliver or update alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        private fun startAlarmService(
            context: Context,
            payload: AlarmLaunchPayload
        ) {
            val serviceIntent =
                AlarmIntentContract.write(
                    Intent(context, AlarmService::class.java),
                    payload
                )
            context.startForegroundService(serviceIntent)
        }
        internal fun shouldDeliver(alarm: Alarm?): Boolean = alarm?.isEnabled == true

        internal fun payloadForDelivery(
            alarm: Alarm,
            scheduledPayload: AlarmLaunchPayload
        ) = when (scheduledPayload.launchType) {
            AlarmLaunchType.MAIN -> AlarmLaunchPayload.fromAlarm(alarm)
            AlarmLaunchType.WAKE_UP_CHECK -> scheduledPayload
        }
    }
}
