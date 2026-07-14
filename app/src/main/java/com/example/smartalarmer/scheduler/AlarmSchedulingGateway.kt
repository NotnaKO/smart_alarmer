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

    override fun schedule(alarm: Alarm): AlarmScheduleResult = AlarmScheduler.schedule(applicationContext, alarm)

    override fun cancel(alarm: Alarm): AlarmCancelResult = AlarmScheduler.cancel(applicationContext, alarm)
}
