package com.example.smartalarmer.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSessionStoreTest {
    @Test
    fun replacementAndRedeliveryPreserveOriginalVolumeUntilClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AlarmSessionStore(context)
        store.clear()

        assertEquals(3, store.begin(AlarmLaunchPayload(alarmId = 1), currentVolume = 3).originalVolume)
        assertEquals(3, store.begin(AlarmLaunchPayload(alarmId = 2), currentVolume = 10).originalVolume)
        assertEquals(2, store.current()?.alarmId)

        store.clear()
        assertNull(store.current())
    }

    @Test
    fun redeliveryRestoresPayloadAndTaskProgress() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AlarmSessionStore(context)
        store.clear()
        val payload =
            AlarmLaunchPayload(
                alarmId = 9,
                puzzlesList = "MATH,TYPING",
                puzzleCount = 2,
                alarmLabel = "Morning",
                volumeRampSeconds = 120
            )

        store.begin(payload, currentVolume = 4)
        store.updateActiveTaskIndex(alarmId = 9, taskIndex = 1)

        val restored = AlarmSessionStore(context).current()
        assertEquals(payload, restored?.payload)
        assertEquals(1, restored?.activeTaskIndex)
        store.clear()
    }

    @Test
    fun launcherRecoveryRecreatesDismissIntentForActiveAlarm() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AlarmSessionStore(context)
        store.clear()
        val payload =
            AlarmLaunchPayload(
                alarmId = 17,
                puzzlesList = "MEMORY",
                alarmLabel = "Wake",
                occurrenceTriggerAtMillis = 123_456L
            )
        store.begin(payload, currentVolume = 3)

        val recovered = requireNotNull(ActiveAlarmRecovery.createIntent(context))

        assertEquals(payload, AlarmIntentContract.read(recovered))
        ActiveAlarmRecovery.markDismissRequested(context, payload.alarmId)
        assertNull(ActiveAlarmRecovery.createIntent(context))
        store.clear()
    }
}
