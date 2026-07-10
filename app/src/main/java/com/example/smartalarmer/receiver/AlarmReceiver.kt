package com.example.smartalarmer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val puzzlesList = intent.getStringExtra("PUZZLES_LIST") ?: "MATH"
        val puzzleCount = intent.getIntExtra("PUZZLE_COUNT", 2)
        val isGradualVolume = intent.getBooleanExtra("IS_GRADUAL_VOLUME", true)
        val soundUri = intent.getStringExtra("SOUND_URI")
        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: ""
        val isPreview = intent.getBooleanExtra("IS_PREVIEW", false)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("PUZZLES_LIST", puzzlesList)
            putExtra("PUZZLE_COUNT", puzzleCount)
            putExtra("IS_GRADUAL_VOLUME", isGradualVolume)
            putExtra("SOUND_URI", soundUri)
            putExtra("ALARM_LABEL", alarmLabel)
            putExtra("IS_PREVIEW", isPreview)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        if (alarmId != -1) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AlarmDatabase.getDatabase(context)
                    val alarmDao = database.alarmDao()
                    val alarm = alarmDao.getAlarmById(alarmId)
                    if (alarm != null) {
                        if (alarm.daysOfWeek.isNotEmpty()) {
                            // Recurring alarm: schedule next occurrence
                            AlarmScheduler.schedule(context, alarm)
                        } else {
                            // One-time alarm: disable it
                            val updated = alarm.copy(isEnabled = false)
                            alarmDao.updateAlarm(updated)
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
