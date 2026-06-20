package com.example.smartalarmer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AlarmDatabase.getDatabase(context)
                    val activeAlarms = database.alarmDao().getEnabledAlarms()
                    for (alarm in activeAlarms) {
                        AlarmScheduler.schedule(context, alarm)
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
