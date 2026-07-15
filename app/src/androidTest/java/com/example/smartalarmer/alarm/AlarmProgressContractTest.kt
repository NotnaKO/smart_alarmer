package com.example.smartalarmer.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmProgressContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun verifiedProgressRoundTripsWithAlarmAndTaskIdentity() {
        val event =
            AlarmProgressEvent(
                alarmId = 42,
                taskIndex = 2,
                type = AlarmProgressEventType.VERIFIED_PROGRESS,
                progress = 0.75f
            )

        assertEquals(event, AlarmProgressContract.read(AlarmProgressContract.intent(context, event)))
    }

    @Test
    fun malformedOrOutOfRangeProgressIsRejected() {
        assertNull(AlarmProgressContract.read(Intent("another.action")))

        val invalid =
            AlarmProgressContract
                .intent(
                    context,
                    AlarmProgressEvent(
                        alarmId = 42,
                        taskIndex = 0,
                        type = AlarmProgressEventType.VERIFIED_PROGRESS,
                        progress = 0.5f
                    )
                ).putExtra("alarm_progress_value", 1.5f)
        assertNull(AlarmProgressContract.read(invalid))
    }
}
