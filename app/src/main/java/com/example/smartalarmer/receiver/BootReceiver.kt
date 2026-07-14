package com.example.smartalarmer.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import com.example.smartalarmer.scheduler.RescheduleEnabledAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val isExactAlarmPermissionEvent =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        val canScheduleExactAlarms =
            if (isExactAlarmPermissionEvent) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

        if (shouldReschedule(intent.action, canScheduleExactAlarms)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository =
                        RoomAlarmRepository(
                            AlarmDatabase.getDatabase(context).alarmDao()
                        )
                    val report =
                        RescheduleEnabledAlarms(
                            repository,
                            AndroidAlarmSchedulingGateway(context)
                        )()
                    report.failures.forEach { result ->
                        when (result) {
                            AlarmScheduleResult.PermissionRequired ->
                                android.util.Log.w(
                                    TAG,
                                    "Exact alarm permission is required while restoring alarms"
                                )
                            is AlarmScheduleResult.Failure ->
                                android.util.Log.e(
                                    TAG,
                                    "Unable to restore an alarm",
                                    result.exception
                                )
                            is AlarmScheduleResult.Scheduled -> Unit
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to restore alarms", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        internal fun shouldReschedule(
            action: String?,
            canScheduleExactAlarms: Boolean
        ): Boolean = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
            -> true
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                canScheduleExactAlarms
            else -> false
        }
    }
}
