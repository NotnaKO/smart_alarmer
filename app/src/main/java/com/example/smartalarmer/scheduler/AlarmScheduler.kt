package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.ui.main.MainActivity
import java.util.Calendar

sealed interface AlarmScheduleResult {
    data class Scheduled(val triggerAtMillis: Long) : AlarmScheduleResult
    data object PermissionRequired : AlarmScheduleResult
    data class Failure(val exception: Exception) : AlarmScheduleResult
}

object AlarmScheduler {
    fun calculateNextTriggerTime(alarm: Alarm, now: Calendar): Calendar {
        val calendar = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val activeDays = alarm.repeatDays.values
            .map { day ->
                when (day.isoValue) {
                    1 -> Calendar.MONDAY
                    2 -> Calendar.TUESDAY
                    3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY
                    6 -> Calendar.SATURDAY
                    7 -> Calendar.SUNDAY
                    else -> error("Unsupported ISO day ${day.isoValue}")
                }
            }
            .toSet()

        if (activeDays.isNotEmpty()) {
            var found = false
            for (i in 0..7) {
                val checkTime = (calendar.clone() as Calendar).apply {
                    add(Calendar.DATE, i)
                }
                val dayOfWeek = checkTime.get(Calendar.DAY_OF_WEEK)
                if (activeDays.contains(dayOfWeek)) {
                    if (checkTime.after(now)) {
                        calendar.add(Calendar.DATE, i)
                        found = true
                        break
                    }
                }
            }
            if (!found && calendar.before(now)) {
                calendar.add(Calendar.DATE, 1)
            }
        } else {
            // One-time alarm
            if (calendar.before(now)) {
                calendar.add(Calendar.DATE, 1)
            }
        }
        return calendar
    }

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, alarm: Alarm): AlarmScheduleResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextTrigger = calculateNextTriggerTime(alarm, Calendar.getInstance())
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
                triggerAtMillis = nextTrigger.timeInMillis,
                canScheduleExactAlarm = canScheduleExactAlarm
            ) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(nextTrigger.timeInMillis, showIntent),
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
