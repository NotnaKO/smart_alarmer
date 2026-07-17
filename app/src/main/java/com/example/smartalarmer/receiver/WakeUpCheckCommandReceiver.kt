package com.example.smartalarmer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.scheduler.AndroidWakeUpCheckSchedulingGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WakeUpCheckCommandReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AlarmDatabase.getDatabase(context)
                val coordinator =
                    WakeUpCheckCoordinator(
                        alarmRepository = RoomAlarmRepository(database.alarmDao()),
                        sessionDao = database.wakeUpCheckDao(),
                        scheduler = AndroidWakeUpCheckSchedulingGateway(context)
                    )
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
                if (alarmId < 0) return@launch
                when (intent.action) {
                    ACTION_START -> coordinator.start(alarmId)
                    ACTION_COMPLETE ->
                        coordinator.complete(
                            alarmId = alarmId,
                            token = intent.getStringExtra(EXTRA_TOKEN).orEmpty(),
                            checkNumber = intent.getIntExtra(EXTRA_CHECK_NUMBER, 0)
                        )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to apply wake-up check command", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WakeUpCheckCommand"
        private const val ACTION_START = "com.example.smartalarmer.action.START_WAKE_UP_CHECKS"
        private const val ACTION_COMPLETE = "com.example.smartalarmer.action.COMPLETE_WAKE_UP_CHECK"
        private const val EXTRA_ALARM_ID = "ALARM_ID"
        private const val EXTRA_TOKEN = "TOKEN"
        private const val EXTRA_CHECK_NUMBER = "CHECK_NUMBER"

        fun startIntent(
            context: Context,
            alarmId: Int
        ): Intent = commandIntent(context, ACTION_START, alarmId)

        fun completeIntent(
            context: Context,
            alarmId: Int,
            token: String,
            checkNumber: Int
        ): Intent = commandIntent(context, ACTION_COMPLETE, alarmId).apply {
            putExtra(EXTRA_TOKEN, token)
            putExtra(EXTRA_CHECK_NUMBER, checkNumber)
        }

        private fun commandIntent(
            context: Context,
            action: String,
            alarmId: Int
        ): Intent = Intent(context, WakeUpCheckCommandReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
    }
}
