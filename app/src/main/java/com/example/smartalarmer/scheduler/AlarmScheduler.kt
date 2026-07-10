package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.ui.main.MainActivity
import java.time.Clock
import java.time.ZoneId

sealed interface AlarmScheduleResult {
    data class Scheduled(val triggerAtMillis: Long) : AlarmScheduleResult
    data object PermissionRequired : AlarmScheduleResult
    data class Failure(val exception: Exception) : AlarmScheduleResult
}

object AlarmScheduler {
    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        alarm: Alarm,
        clock: Clock = Clock.systemUTC(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AlarmScheduleResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextTriggerMillis = AlarmTimeCalculator(clock, zoneId).nextTrigger(alarm).toEpochMilli()
            val canScheduleExactAlarm = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("PUZZLES_LIST", alarm.puzzlesList)
                putExtra("PUZZLE_COUNT", alarm.puzzleCount)
                putExtra("IS_GRADUAL_VOLUME", alarm.isGradualVolume)
                putExtra("SOUND_URI", alarm.soundUri)
                putExtra("ALARM_LABEL", alarm.label)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = PendingIntent.getActivity(
                context,
                alarm.id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExact(
                triggerAtMillis = nextTriggerMillis,
                canScheduleExactAlarm = canScheduleExactAlarm
            ) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(nextTriggerMillis, showIntent),
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            AlarmScheduleResult.PermissionRequired
        } catch (e: Exception) {
            AlarmScheduleResult.Failure(e)
        }
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

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarm.id, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
