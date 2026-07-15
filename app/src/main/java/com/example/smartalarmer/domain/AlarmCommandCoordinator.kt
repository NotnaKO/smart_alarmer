package com.example.smartalarmer.domain

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway

sealed interface AlarmCommandResult {
    data class Scheduled(
        val alarm: Alarm,
        val triggerAtMillis: Long
    ) : AlarmCommandResult

    data class Updated(
        val alarm: Alarm
    ) : AlarmCommandResult

    data object Deleted : AlarmCommandResult

    data object PermissionRequired : AlarmCommandResult

    data object NotificationCapabilityRequired : AlarmCommandResult

    data class SchedulingFailed(
        val exception: Exception
    ) : AlarmCommandResult

    data class PersistenceFailed(
        val exception: Exception
    ) : AlarmCommandResult

    data class CancellationFailed(
        val exception: Exception
    ) : AlarmCommandResult
}

class AlarmCommandCoordinator(
    private val repository: AlarmRepository,
    private val scheduler: AlarmSchedulingGateway,
    private val activationGate: AlarmActivationGate = AlarmActivationGate.ALWAYS_READY
) {
    suspend fun create(draft: AlarmDraft): AlarmCommandResult {
        if (!activationGate.isNotificationDeliveryReady()) {
            return AlarmCommandResult.NotificationCapabilityRequired
        }
        val inserted =
            try {
                repository.insertAlarm(draft.toAlarm(isEnabled = false))
            } catch (e: Exception) {
                return AlarmCommandResult.PersistenceFailed(e)
            }
        val candidate = inserted.copy(isEnabled = true)
        return when (val schedule = scheduler.schedule(candidate)) {
            is AlarmScheduleResult.Scheduled -> {
                val scheduledCandidate = candidate.withScheduleResult(schedule)
                try {
                    repository.updateAlarm(scheduledCandidate)
                    AlarmCommandResult.Scheduled(scheduledCandidate, schedule.triggerAtMillis)
                } catch (e: Exception) {
                    when (val cancellation = scheduler.cancel(candidate)) {
                        AlarmCancelResult.Cancelled -> {
                            runCatching { repository.deleteAlarm(inserted) }
                            AlarmCommandResult.PersistenceFailed(e)
                        }
                        is AlarmCancelResult.Failure ->
                            AlarmCommandResult.CancellationFailed(cancellation.exception)
                    }
                }
            }
            AlarmScheduleResult.PermissionRequired -> {
                runCatching { repository.deleteAlarm(inserted) }
                AlarmCommandResult.PermissionRequired
            }
            is AlarmScheduleResult.Failure -> {
                runCatching { repository.deleteAlarm(inserted) }
                AlarmCommandResult.SchedulingFailed(schedule.exception)
            }
        }
    }

    suspend fun update(
        original: Alarm,
        draft: AlarmDraft
    ): AlarmCommandResult {
        if (!activationGate.isNotificationDeliveryReady()) {
            return AlarmCommandResult.NotificationCapabilityRequired
        }
        val candidate = draft.toAlarm(existing = original, isEnabled = true)
        return when (val schedule = scheduler.schedule(candidate)) {
            is AlarmScheduleResult.Scheduled -> {
                val scheduledCandidate = candidate.withScheduleResult(schedule)
                try {
                    repository.updateAlarm(scheduledCandidate)
                    AlarmCommandResult.Scheduled(scheduledCandidate, schedule.triggerAtMillis)
                } catch (e: Exception) {
                    restore(original, candidate)
                    AlarmCommandResult.PersistenceFailed(e)
                }
            }
            AlarmScheduleResult.PermissionRequired -> AlarmCommandResult.PermissionRequired
            is AlarmScheduleResult.Failure -> AlarmCommandResult.SchedulingFailed(schedule.exception)
        }
    }

    suspend fun setEnabled(
        alarm: Alarm,
        enabled: Boolean
    ): AlarmCommandResult {
        val candidate = alarm.copy(isEnabled = enabled)
        if (enabled) {
            if (!activationGate.isNotificationDeliveryReady()) {
                return AlarmCommandResult.NotificationCapabilityRequired
            }
            return when (val schedule = scheduler.schedule(candidate)) {
                is AlarmScheduleResult.Scheduled -> {
                    val scheduledCandidate = candidate.withScheduleResult(schedule)
                    try {
                        repository.updateAlarm(scheduledCandidate)
                        AlarmCommandResult.Scheduled(scheduledCandidate, schedule.triggerAtMillis)
                    } catch (e: Exception) {
                        when (val cancellation = scheduler.cancel(candidate)) {
                            AlarmCancelResult.Cancelled -> AlarmCommandResult.PersistenceFailed(e)
                            is AlarmCancelResult.Failure ->
                                AlarmCommandResult.CancellationFailed(cancellation.exception)
                        }
                    }
                }
                AlarmScheduleResult.PermissionRequired -> AlarmCommandResult.PermissionRequired
                is AlarmScheduleResult.Failure -> AlarmCommandResult.SchedulingFailed(schedule.exception)
            }
        }

        return when (val cancellation = scheduler.cancel(alarm)) {
            AlarmCancelResult.Cancelled -> {
                try {
                    val disabled = candidate.copy(
                        scheduleStatus = AlarmScheduleStatus.DISABLED.name,
                        scheduledTriggerAtMillis = null
                    )
                    repository.updateAlarm(disabled)
                    AlarmCommandResult.Updated(disabled)
                } catch (e: Exception) {
                    if (alarm.isEnabled) scheduler.schedule(alarm)
                    AlarmCommandResult.PersistenceFailed(e)
                }
            }
            is AlarmCancelResult.Failure -> AlarmCommandResult.CancellationFailed(cancellation.exception)
        }
    }

    suspend fun delete(alarm: Alarm): AlarmCommandResult = when (val cancellation = scheduler.cancel(alarm)) {
        AlarmCancelResult.Cancelled -> {
            try {
                repository.deleteAlarm(alarm)
                AlarmCommandResult.Deleted
            } catch (e: Exception) {
                if (alarm.isEnabled) scheduler.schedule(alarm)
                AlarmCommandResult.PersistenceFailed(e)
            }
        }
        is AlarmCancelResult.Failure -> AlarmCommandResult.CancellationFailed(cancellation.exception)
    }

    private fun restore(
        original: Alarm,
        candidate: Alarm
    ) {
        if (original.isEnabled) {
            scheduler.schedule(original)
        } else {
            scheduler.cancel(candidate)
        }
    }

    private fun Alarm.withScheduleResult(result: AlarmScheduleResult.Scheduled): Alarm = copy(
        scheduleStatus = AlarmScheduleStatus.SCHEDULED.name,
        scheduledTriggerAtMillis = result.triggerAtMillis
    )
}
