package com.example.smartalarmer.service

import android.content.Context
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs independently of the service teardown so a fast puzzle completion cannot cancel the
 * durable one-time disable or recurring reschedule after delivery was confirmed.
 */
internal class AlarmDeliveryFollowUp(
    context: Context
) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coordinator =
        AlarmDeliveryCoordinator(
            repository = RoomAlarmRepository(AlarmDatabase.getDatabase(applicationContext).alarmDao()),
            scheduler = AndroidAlarmSchedulingGateway(applicationContext)
        )

    fun start(alarmId: Int) {
        scope.launch {
            runCatching { coordinator.onAlarmSessionStarted(alarmId) }
                .onFailure { error ->
                    android.util.Log.e("AlarmService", "Unable to persist alarm delivery follow-up", error)
                }
        }
    }
}
