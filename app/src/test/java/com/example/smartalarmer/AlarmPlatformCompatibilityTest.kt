package com.example.smartalarmer

import android.app.ActivityOptions
import com.example.smartalarmer.service.AlarmNotification
import com.example.smartalarmer.service.AlarmScreenLauncher
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

        assertNull(AlarmScreenLauncher.senderBackgroundStartMode(33))
        @Suppress("DEPRECATION")
        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            AlarmScreenLauncher.senderBackgroundStartMode(34)
        )
        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            AlarmScreenLauncher.senderBackgroundStartMode(36)
        )
    }

    @Test
    fun immediateAlarmScreenLaunchOnlyAppliesWhileUnlockedAndInteractive() {
        assertTrue(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isInteractive = true,
                isKeyguardLocked = false
            )
        )
        assertFalse(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isInteractive = false,
                isKeyguardLocked = false
            )
        )
        assertFalse(
            AlarmScreenLauncher.shouldLaunchImmediately(
                isInteractive = true,
                isKeyguardLocked = true
            )
        )
    }
}
