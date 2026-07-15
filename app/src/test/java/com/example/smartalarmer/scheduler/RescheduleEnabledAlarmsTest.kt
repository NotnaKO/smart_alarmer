package com.example.smartalarmer.scheduler

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RescheduleEnabledAlarmsTest {
    @Test
    fun reconciliationPersistsSuccessAndFailureByAlarm() = runTest {
        val repository = RescheduleRepository(listOf(alarm(1), alarm(2)))
        val scheduler =
            object : AlarmSchedulingGateway {
                override fun schedule(alarm: Alarm): AlarmScheduleResult = if (alarm.id == 1) AlarmScheduleResult.Scheduled(10_000L) else AlarmScheduleResult.PermissionRequired

                override fun cancel(alarm: Alarm): AlarmCancelResult = AlarmCancelResult.Cancelled
            }

        val report = RescheduleEnabledAlarms(repository, scheduler)()

        assertEquals(1, report.scheduledCount)
        assertEquals(2, report.failures.single().alarmId)
        assertEquals(AlarmScheduleStatus.SCHEDULED.name, repository.items.value[0].scheduleStatus)
        assertEquals(10_000L, repository.items.value[0].scheduledTriggerAtMillis)
        assertEquals(AlarmScheduleStatus.PERMISSION_REQUIRED.name, repository.items.value[1].scheduleStatus)
    }

    private fun alarm(id: Int) = Alarm(
        id = id,
        hour = 7,
        minute = 0,
        daysOfWeek = "1",
        puzzlesList = "MATH"
    )
}

private class RescheduleRepository(initial: List<Alarm>) : AlarmRepository {
    val items = MutableStateFlow(initial)
    override val alarms: Flow<List<Alarm>> = items
    override suspend fun getEnabledAlarms(): List<Alarm> = items.value.filter(Alarm::isEnabled)
    override suspend fun getAlarmById(id: Int): Alarm? = items.value.firstOrNull { it.id == id }
    override suspend fun insertAlarm(alarm: Alarm): Alarm = alarm
    override suspend fun updateAlarm(alarm: Alarm) {
        items.value = items.value.map { if (it.id == alarm.id) alarm else it }
    }
    override suspend fun deleteAlarm(alarm: Alarm) = Unit
}
