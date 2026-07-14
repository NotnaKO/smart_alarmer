package com.example.smartalarmer

import android.app.ActivityOptions
import com.example.smartalarmer.service.AlarmNotification
import com.example.smartalarmer.utils.AlarmCapabilityChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlatformCompatibilityTest {
    @Test
    fun exactAlarmSpecialAccessOnlyAppliesToAndroid12() {
        assertFalse(AlarmCapabilityChecker.requiresExactAlarmSpecialAccess(30))
        assertTrue(AlarmCapabilityChecker.requiresExactAlarmSpecialAccess(31))
        assertTrue(AlarmCapabilityChecker.requiresExactAlarmSpecialAccess(32))
        assertFalse(AlarmCapabilityChecker.requiresExactAlarmSpecialAccess(33))
        assertFalse(AlarmCapabilityChecker.requiresExactAlarmSpecialAccess(36))
    }

    @Test
    fun backgroundStartModeTracksPlatformContract() {
        assertNull(AlarmNotification.creatorBackgroundStartMode(33))
        @Suppress("DEPRECATION")
        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            AlarmNotification.creatorBackgroundStartMode(34)
        )
        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            AlarmNotification.creatorBackgroundStartMode(36)
        )
    }
}
