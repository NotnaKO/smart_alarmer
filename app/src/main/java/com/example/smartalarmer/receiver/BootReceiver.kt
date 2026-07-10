package com.example.smartalarmer.receiver

import android.content.BroadcastReceiver
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isBootCompleted = intent.action == Intent.ACTION_BOOT_COMPLETED
        val isExactAlarmPermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        if (isBootCompleted || isExactAlarmPermissionGranted) {
            if (isExactAlarmPermissionGranted) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AlarmDatabase.getDatabase(context)
                    val activeAlarms = database.alarmDao().getEnabledAlarms()
                    for (alarm in activeAlarms) {
                        when (val result = AlarmScheduler.schedule(context, alarm)) {
                            AlarmScheduleResult.PermissionRequired -> android.util.Log.w(
                                "BootReceiver",
                                "Exact alarm permission is required to reschedule alarm ${alarm.id}"
                            )
                            is AlarmScheduleResult.Failure -> android.util.Log.e(
                                "BootReceiver",
                                "Unable to reschedule alarm ${alarm.id}",
                                result.exception
                            )
                            is AlarmScheduleResult.Scheduled -> Unit
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
