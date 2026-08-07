package com.example.smartalarmer.service

import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway

/** Applies the durable follow-up only after the foreground alarm session has initialized. */
internal class AlarmDeliveryCoordinator(
    private val repository: AlarmRepository,
    private val scheduler: AlarmSchedulingGateway
) {
    suspend fun onAlarmSessionStarted(alarmId: Int) {
        val alarm = repository.getAlarmById(alarmId) ?: return
        if (!alarm.isEnabled) return

        if (alarm.repeatDays.isOneTime) {
            scheduler.cancel(alarm)
            repository.updateAlarm(
                alarm.copy(
                    isEnabled = false,
                    scheduleStatus = AlarmScheduleStatus.DISABLED.name,
                    scheduledTriggerAtMillis = null
                )
            )
            return
        }

        val candidate = alarm.copy(suppressedThroughEpochDay = null)
        val updated =
            when (val result = scheduler.schedule(candidate)) {
                is AlarmScheduleResult.Scheduled ->
                    candidate.copy(
                        scheduleStatus = AlarmScheduleStatus.SCHEDULED.name,
                        scheduledTriggerAtMillis = result.triggerAtMillis
                    )
                AlarmScheduleResult.PermissionRequired ->
                    candidate.copy(
                        scheduleStatus = AlarmScheduleStatus.PERMISSION_REQUIRED.name,
                        scheduledTriggerAtMillis = null
                    )
                is AlarmScheduleResult.Failure ->
                    candidate.copy(
                        scheduleStatus = AlarmScheduleStatus.FAILED.name,
                        scheduledTriggerAtMillis = null
                    )
            }
        repository.updateAlarm(updated)
    }
}
