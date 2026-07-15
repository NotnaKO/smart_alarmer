package com.example.smartalarmer.service

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmDeliveryCoordinatorTest {
    @Test
    fun oneTimeAlarmIsDisabledOnlyAfterSessionStartIsConfirmed() = runTest {
        val repository = DeliveryRepository(alarm(days = ""))

        AlarmDeliveryCoordinator(repository, DeliveryScheduler()).onAlarmSessionStarted(1)

        val updated = repository.items.value.single()
        assertFalse(updated.isEnabled)
        assertEquals(AlarmScheduleStatus.DISABLED.name, updated.scheduleStatus)
    }

    @Test
    fun recurringAlarmPersistsNextScheduledTrigger() = runTest {
        val repository = DeliveryRepository(alarm(days = "1,3"))

        AlarmDeliveryCoordinator(repository, DeliveryScheduler()).onAlarmSessionStarted(1)

        val updated = repository.items.value.single()
        assertEquals(AlarmScheduleStatus.SCHEDULED.name, updated.scheduleStatus)
        assertEquals(12_345L, updated.scheduledTriggerAtMillis)
    }

    @Test
    fun staleOrDisabledDeliveryDoesNotMutateOrReschedule() = runTest {
        val existing = alarm(days = "1", enabled = false)
        val repository = DeliveryRepository(existing)
        val scheduler = DeliveryScheduler()

        AlarmDeliveryCoordinator(repository, scheduler).onAlarmSessionStarted(1)

        assertEquals(existing, repository.items.value.single())
        assertEquals(0, scheduler.scheduleCalls)
    }

    private fun alarm(
        days: String,
        enabled: Boolean = true
    ) = Alarm(
        id = 1,
        hour = 7,
        minute = 0,
        daysOfWeek = days,
        isEnabled = enabled,
        puzzlesList = "MATH"
    )
}

private class DeliveryRepository(initial: Alarm) : AlarmRepository {
    val items = MutableStateFlow(listOf(initial))
    override val alarms: Flow<List<Alarm>> = items

    override suspend fun getEnabledAlarms(): List<Alarm> = items.value.filter(Alarm::isEnabled)
    override suspend fun getAlarmById(id: Int): Alarm? = items.value.firstOrNull { it.id == id }
    override suspend fun insertAlarm(alarm: Alarm): Alarm = alarm
    override suspend fun updateAlarm(alarm: Alarm) {
        items.value = items.value.map { if (it.id == alarm.id) alarm else it }
    }
    override suspend fun deleteAlarm(alarm: Alarm) = Unit
}

private class DeliveryScheduler : AlarmSchedulingGateway {
    var scheduleCalls = 0
    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        scheduleCalls++
        return AlarmScheduleResult.Scheduled(12_345L)
    }

    override fun cancel(alarm: Alarm): AlarmCancelResult = AlarmCancelResult.Cancelled
}
