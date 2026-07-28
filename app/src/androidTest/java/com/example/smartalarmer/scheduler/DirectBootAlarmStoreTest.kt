package com.example.smartalarmer.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectBootAlarmStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun snapshotAndDeferredEventsPersistInDeviceProtectedStorage() {
        val store = DirectBootAlarmStore(context)
        store.retainAlarmIds(emptySet())
        val alarm =
            Alarm(
                id = 17,
                hour = 6,
                minute = 45,
                daysOfWeek = "1,3,5",
                puzzlesList = "MATH,TYPING",
                suppressedThroughEpochDay = 20_400L,
                oneTimeDateEpochDay = 20_500L
            )

        store.upsert(alarm, triggerAtMillis = 123_456L)
        store.markDeliveryForUnlock(alarm.id)
        store.markDismissalForUnlock(alarm.id)

        assertEquals(DirectBootAlarmSnapshot(alarm, 123_456L), store.snapshots().single())
        assertTrue(alarm.id in store.deliveredAlarmIds())
        assertTrue(alarm.id in store.dismissedAlarmIds())

        store.remove(alarm.id)
        store.clearDeliveredAlarmId(alarm.id)
        store.clearDismissedAlarmId(alarm.id)
    }

    @Test
    fun adjustedLockedBootSnapshotAuthorizesDeliveryAfterUnlock() {
        val store = DirectBootAlarmStore(context)
        store.retainAlarmIds(emptySet())
        val roomTrigger = 100_000L
        val recoveredTrigger = 101_000L
        val alarm =
            Alarm(
                id = 23,
                hour = 6,
                minute = 45,
                daysOfWeek = "1,2,3,4,5,6,7",
                puzzlesList = "MATH",
                scheduledTriggerAtMillis = roomTrigger
            )
        store.upsert(alarm, recoveredTrigger)

        val snapshot = store.snapshots().single()

        assertTrue(
            AlarmReceiver.shouldDeliver(
                alarm = alarm,
                occurrenceTriggerAtMillis = recoveredTrigger,
                directBootTriggerAtMillis = snapshot.triggerAtMillis
            )
        )
        store.remove(alarm.id)
    }
}
