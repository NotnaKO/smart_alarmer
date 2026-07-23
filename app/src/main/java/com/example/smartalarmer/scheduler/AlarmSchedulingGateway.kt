package com.example.smartalarmer.scheduler

import android.content.Context
import com.example.smartalarmer.data.Alarm

interface AlarmSchedulingGateway {
    fun schedule(alarm: Alarm): AlarmScheduleResult

    fun cancel(alarm: Alarm): AlarmCancelResult
}

class AndroidAlarmSchedulingGateway(
    context: Context
) : AlarmSchedulingGateway {
    private val applicationContext = context.applicationContext
    private val directBootStore = DirectBootAlarmStore(applicationContext)

    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        val result = AlarmScheduler.schedule(applicationContext, alarm)
        if (result is AlarmScheduleResult.Scheduled) {
            try {
                directBootStore.upsert(alarm, result.triggerAtMillis)
            } catch (error: Exception) {
                AlarmScheduler.cancel(applicationContext, alarm)
                return AlarmScheduleResult.Failure(error)
            }
        }
        return result
    }

    override fun cancel(alarm: Alarm): AlarmCancelResult {
        try {
            directBootStore.remove(alarm.id)
        } catch (error: Exception) {
            return AlarmCancelResult.Failure(error)
        }
        return AlarmScheduler.cancel(applicationContext, alarm)
    }
}
