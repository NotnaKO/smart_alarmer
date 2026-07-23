package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.ui.main.MainActivity
import java.time.Clock
import java.time.ZoneId

sealed interface AlarmScheduleResult {
    data class Scheduled(
        val triggerAtMillis: Long
    ) : AlarmScheduleResult

    data object PermissionRequired : AlarmScheduleResult

    data class Failure(
        val exception: Exception
    ) : AlarmScheduleResult
}

sealed interface AlarmCancelResult {
    data object Cancelled : AlarmCancelResult

    data class Failure(
        val exception: Exception
    ) : AlarmCancelResult
}

object AlarmScheduler {
    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        alarm: Alarm,
        clock: Clock = Clock.systemUTC(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AlarmScheduleResult {
        val nextTriggerMillis =
            try {
                AlarmTimeCalculator(clock, zoneId).nextTrigger(alarm).toEpochMilli()
            } catch (e: Exception) {
                return AlarmScheduleResult.Failure(e)
            }
        return scheduleAt(context, alarm, nextTriggerMillis)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAt(
        context: Context,
        alarm: Alarm,
        triggerAtMillis: Long
    ): AlarmScheduleResult = try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleExactAlarm =
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
        if (!canScheduleExactAlarm) {
            AlarmScheduleResult.PermissionRequired
        } else {
            val intent =
                AlarmIntentContract.write(
                    Intent(context, AlarmReceiver::class.java),
                    AlarmLaunchPayload.fromAlarm(alarm)
                )
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    alarm.id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            val showIntent =
                PendingIntent.getActivity(
                    context,
                    alarm.id,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

            scheduleExact(
                triggerAtMillis = triggerAtMillis,
                canScheduleExactAlarm = true
            ) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
            }
        }
    } catch (_: SecurityException) {
        AlarmScheduleResult.PermissionRequired
    } catch (e: Exception) {
        AlarmScheduleResult.Failure(e)
    }

    internal fun scheduleExact(
        triggerAtMillis: Long,
        canScheduleExactAlarm: Boolean,
        scheduleAction: () -> Unit
    ): AlarmScheduleResult {
        if (!canScheduleExactAlarm) return AlarmScheduleResult.PermissionRequired

        return try {
            scheduleAction()
            AlarmScheduleResult.Scheduled(triggerAtMillis)
        } catch (_: SecurityException) {
            AlarmScheduleResult.PermissionRequired
        } catch (e: Exception) {
            AlarmScheduleResult.Failure(e)
        }
    }

    fun cancel(
        context: Context,
        alarm: Alarm
    ): AlarmCancelResult = try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                alarm.id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        AlarmCancelResult.Cancelled
    } catch (e: Exception) {
        AlarmCancelResult.Failure(e)
    }
}
