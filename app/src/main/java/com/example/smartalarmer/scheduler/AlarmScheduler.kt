package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {
    fun calculateNextTriggerTime(alarm: Alarm, now: Calendar): Calendar {
        val calendar = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val activeDays = alarm.daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .mapNotNull { day ->
                when (day) {
                    1 -> Calendar.MONDAY
                    2 -> Calendar.TUESDAY
                    3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY
                    6 -> Calendar.SATURDAY
                    7 -> Calendar.SUNDAY
                    else -> null
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
    fun schedule(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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

        val nextTrigger = calculateNextTriggerTime(alarm, Calendar.getInstance())

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTrigger.timeInMillis,
            pendingIntent
        )
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
