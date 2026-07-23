package com.example.smartalarmer.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.data.Alarm
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
                puzzlesList = "MATH,TYPING"
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
}
