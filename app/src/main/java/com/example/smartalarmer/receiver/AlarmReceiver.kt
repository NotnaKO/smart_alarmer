package com.example.smartalarmer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
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
                val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
                val alarm = alarmDao.getAlarmById(payload.alarmId)
                if (!shouldDeliver(alarm) || alarm == null) {
                    Log.i(TAG, "Ignoring stale alarm delivery for id ${payload.alarmId}")
                    return@launch
                }

                try {
                    startAlarmService(context, AlarmLaunchPayload.fromAlarm(alarm))
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to start alarm service for ${alarm.id}", e)
                }
                when (followUpFor(alarm)) {
                    AlarmFollowUp.NONE -> Unit
                    AlarmFollowUp.RESCHEDULE -> {
                        when (val result = AlarmScheduler.schedule(context, alarm)) {
                            AlarmScheduleResult.PermissionRequired ->
                                Log.w(
                                    TAG,
                                    "Exact alarm permission is required to reschedule alarm ${alarm.id}"
                                )
                            is AlarmScheduleResult.Failure ->
                                Log.e(TAG, "Unable to reschedule alarm ${alarm.id}", result.exception)
                            is AlarmScheduleResult.Scheduled -> Unit
                        }
                    }
                    AlarmFollowUp.DISABLE -> alarmDao.updateAlarm(alarm.copy(isEnabled = false))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        internal fun followUpFor(alarm: Alarm): AlarmFollowUp = when {
            !alarm.isEnabled -> AlarmFollowUp.NONE
            alarm.repeatDays.isOneTime -> AlarmFollowUp.DISABLE
            else -> AlarmFollowUp.RESCHEDULE
        }

        internal fun shouldDeliver(alarm: Alarm?): Boolean = alarm?.isEnabled == true
    }
}

internal enum class AlarmFollowUp {
    NONE,
    RESCHEDULE,
    DISABLE
}
