package com.example.smartalarmer.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        assertEquals(3, store.begin(alarmId = 1, currentVolume = 3).originalVolume)
        assertEquals(3, store.begin(alarmId = 2, currentVolume = 10).originalVolume)
        assertEquals(2, store.current()?.alarmId)

        store.clear()
        assertNull(store.current())
    }
}
