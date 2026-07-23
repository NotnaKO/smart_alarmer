package com.example.smartalarmer.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingAlarmQueueStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun queuePersistsOrderAndDeduplicatesRedelivery() {
        val store = PendingAlarmQueueStore(context)
        store.clear()
        val first =
            AlarmLaunchPayload(
                alarmId = 1,
                alarmLabel = "First",
                occurrenceTriggerAtMillis = 100L
            )
        val laterOccurrence =
            first.copy(
                alarmLabel = "Later",
                occurrenceTriggerAtMillis = 200L
            )
        val second =
            AlarmLaunchPayload(
                alarmId = 1,
                alarmLabel = "Check",
                launchType = AlarmLaunchType.WAKE_UP_CHECK,
                wakeUpCheckNumber = 1,
                wakeUpCheckToken = "token"
            )

        assertTrue(store.enqueue(first))
        assertFalse(store.enqueue(first))
        assertTrue(store.enqueue(laterOccurrence))
        assertTrue(store.enqueue(second))

        assertTrue(store.hasPending())
        assertEquals(first, store.peek())
        assertTrue(store.removeHead(first))
        assertEquals(laterOccurrence, store.peek())
        assertTrue(store.removeHead(laterOccurrence))
        assertEquals(second, store.peek())
        assertTrue(store.removeHead(second))
        assertFalse(store.hasPending())
    }

    @Test
    fun failedHeadStartCanBeRetriedWithoutReordering() {
        val store = PendingAlarmQueueStore(context)
        store.clear()
        val first = AlarmLaunchPayload(alarmId = 1, occurrenceTriggerAtMillis = 100L)
        val second = AlarmLaunchPayload(alarmId = 2, occurrenceTriggerAtMillis = 200L)
        store.enqueue(first)
        store.enqueue(second)

        assertEquals(first, store.peek())
        assertFalse(store.removeHead(second))
        assertEquals(first, store.peek())
        assertTrue(store.removeHead(first))
        assertEquals(second, store.peek())

        store.clear()
    }
}
