package com.example.smartalarmer.ui.main

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.domain.AlarmActivationGate
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmSchedulingGateway
import com.example.smartalarmer.scheduler.DeliveryTestSchedulingGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelDeliveryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun scheduleDeliveryTestPublishesPendingStateAndEvent() = runTest(mainDispatcherRule.dispatcher) {
        val triggerAtMillis = System.currentTimeMillis() + 15_000L
        val gateway =
            FakeDeliveryTestGateway(
                scheduleResult = AlarmScheduleResult.Scheduled(triggerAtMillis)
            )
        val viewModel = viewModel(gateway)
        val event = async { viewModel.uiEvents.first() }

        viewModel.scheduleDeliveryTest(alarm())
        runCurrent()

        assertEquals(PendingDeliveryTest(alarmId = 7, triggerAtMillis), viewModel.pendingDeliveryTest.value)
        assertEquals(MainUiEvent.DeliveryTestScheduled(triggerAtMillis), event.await())
        assertEquals(alarm(), gateway.lastAlarm)
    }

    @Test
    fun cancelDeliveryTestClearsPendingState() = runTest(mainDispatcherRule.dispatcher) {
        val triggerAtMillis = System.currentTimeMillis() + 15_000L
        val gateway =
            FakeDeliveryTestGateway(
                scheduleResult = AlarmScheduleResult.Scheduled(triggerAtMillis)
            )
        val viewModel = viewModel(gateway)

        viewModel.scheduleDeliveryTest(alarm())
        runCurrent()
        viewModel.cancelDeliveryTest()
        runCurrent()

        assertEquals(null, viewModel.pendingDeliveryTest.value)
        assertEquals(1, gateway.cancelCount)
    }

    @Test
    fun deliveryTestRequiresNotificationCapability() = runTest(mainDispatcherRule.dispatcher) {
        val gateway = FakeDeliveryTestGateway(AlarmScheduleResult.Scheduled(123L))
        val viewModel =
            viewModel(
                gateway,
                activationGate = AlarmActivationGate { false }
            )
        val event = async { viewModel.uiEvents.first() }

        viewModel.scheduleDeliveryTest(alarm())
        runCurrent()

        assertEquals(MainUiEvent.NotificationCapabilityRequired, event.await())
        assertEquals(null, gateway.lastAlarm)
    }

    private fun viewModel(
        deliveryTestGateway: DeliveryTestSchedulingGateway,
        activationGate: AlarmActivationGate = AlarmActivationGate.ALWAYS_READY
    ) = MainViewModel(
        alarmRepository = EmptyRepository(),
        alarmScheduler = NoOpAlarmScheduler(),
        activationGate = activationGate,
        deliveryTestScheduler = deliveryTestGateway
    )

    private fun alarm() = Alarm(
        id = 7,
        hour = 7,
        minute = 30,
        daysOfWeek = "",
        puzzlesList = "MATH,TYPING",
        puzzleCount = 2,
        label = "Morning"
    )
}

private class FakeDeliveryTestGateway(
    private val scheduleResult: AlarmScheduleResult
) : DeliveryTestSchedulingGateway {
    var lastAlarm: Alarm? = null
    var cancelCount = 0

    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        lastAlarm = alarm
        return scheduleResult
    }

    override fun cancel(): AlarmCancelResult {
        cancelCount++
        return AlarmCancelResult.Cancelled
    }
}

private class NoOpAlarmScheduler : AlarmSchedulingGateway {
    override fun schedule(alarm: Alarm): AlarmScheduleResult = AlarmScheduleResult.Scheduled(1L)

    override fun cancel(alarm: Alarm): AlarmCancelResult = AlarmCancelResult.Cancelled
}

private class EmptyRepository : AlarmRepository {
    override val alarms: Flow<List<Alarm>> = MutableStateFlow(emptyList())

    override suspend fun getEnabledAlarms(): List<Alarm> = emptyList()

    override suspend fun getAlarmById(id: Int): Alarm? = null

    override suspend fun insertAlarm(alarm: Alarm): Alarm = alarm

    override suspend fun updateAlarm(alarm: Alarm) = Unit

    override suspend fun deleteAlarm(alarm: Alarm) = Unit
}
