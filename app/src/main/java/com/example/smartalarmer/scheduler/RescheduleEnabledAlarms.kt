package com.example.smartalarmer.scheduler

import com.example.smartalarmer.data.AlarmRepository

data class AlarmRescheduleReport(
    val scheduledCount: Int,
    val failures: List<AlarmScheduleResult>
)

class RescheduleEnabledAlarms(
    private val repository: AlarmRepository,
    private val scheduler: AlarmSchedulingGateway
) {
    suspend operator fun invoke(): AlarmRescheduleReport {
        var scheduledCount = 0
        val failures = mutableListOf<AlarmScheduleResult>()
        repository.getEnabledAlarms().forEach { alarm ->
            when (val result = scheduler.schedule(alarm)) {
                is AlarmScheduleResult.Scheduled -> scheduledCount++
                else -> failures += result
            }
        }
        return AlarmRescheduleReport(scheduledCount, failures)
    }
}
