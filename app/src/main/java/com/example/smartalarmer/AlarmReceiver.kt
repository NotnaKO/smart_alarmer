package com.example.smartalarmer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", intent.getIntExtra("ALARM_ID", -1))
            putExtra("PUZZLES_LIST", intent.getStringExtra("PUZZLES_LIST") ?: "MATH")
            putExtra("PUZZLE_COUNT", intent.getIntExtra("PUZZLE_COUNT", 2))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
