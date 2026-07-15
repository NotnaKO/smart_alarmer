package com.example.smartalarmer.service

import android.content.Context
import android.content.Intent
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity

object ActiveAlarmRecovery {
    fun createIntent(context: Context): Intent? {
        val session = AlarmSessionStore(context).current() ?: return null
        if (session.dismissRequested) return null
        val payload = session.payload
        if (payload.alarmId == AlarmLaunchPayload.NO_ALARM_ID || payload.isPreview) return null
        return AlarmIntentContract
            .write(Intent(context, AlarmDismissActivity::class.java), payload)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    }

    fun markDismissRequested(
        context: Context,
        alarmId: Int
    ) {
        AlarmSessionStore(context).markDismissRequested(alarmId)
    }
}
