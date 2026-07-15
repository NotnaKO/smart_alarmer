package com.example.smartalarmer.scheduler

import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus

data class AlarmRescheduleFailure(
    val alarmId: Int,
    val result: AlarmScheduleResult
)

data class AlarmRescheduleReport(
    val scheduledCount: Int,
    val failures: List<AlarmRescheduleFailure>
)

class RescheduleEnabledAlarms(
    private val repository: AlarmRepository,
    private val scheduler: AlarmSchedulingGateway
) {
    suspend operator fun invoke(): AlarmRescheduleReport {
        var scheduledCount = 0
        val failures = mutableListOf<AlarmRescheduleFailure>()
        repository.getEnabledAlarms().forEach { alarm ->
            val result = scheduler.schedule(alarm)
            val updatedAlarm =
                when (result) {
                    is AlarmScheduleResult.Scheduled ->
                        alarm.copy(
                            scheduleStatus = AlarmScheduleStatus.SCHEDULED.name,
                            scheduledTriggerAtMillis = result.triggerAtMillis
                        )
                    AlarmScheduleResult.PermissionRequired ->
                        alarm.copy(
                            scheduleStatus = AlarmScheduleStatus.PERMISSION_REQUIRED.name,
                            scheduledTriggerAtMillis = null
                        )
                    is AlarmScheduleResult.Failure ->
                        alarm.copy(
                            scheduleStatus = AlarmScheduleStatus.FAILED.name,
                            scheduledTriggerAtMillis = null
                        )
                }
            try {
                repository.updateAlarm(updatedAlarm)
            } catch (error: Exception) {
                failures += AlarmRescheduleFailure(alarm.id, AlarmScheduleResult.Failure(error))
                return@forEach
            }
            when (result) {
                is AlarmScheduleResult.Scheduled -> scheduledCount++
                else -> failures += AlarmRescheduleFailure(alarm.id, result)
            }
        }
        return AlarmRescheduleReport(scheduledCount, failures)
    }
}
