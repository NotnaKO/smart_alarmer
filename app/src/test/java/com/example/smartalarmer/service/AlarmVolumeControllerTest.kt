package com.example.smartalarmer.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmVolumeControllerTest {
    @Test
    fun initialAlarmRampsFromMinimumToMaximumOverSixtySeconds() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 1_000L)

        assertEquals(1, controller.targetVolume(1_000L))
        assertEquals(5, controller.targetVolume(31_000L))
        assertEquals(10, controller.targetVolume(61_000L))
    }

    @Test
    fun customRampDurationControlsInitialAndResumedRise() {
        val controller =
            AlarmVolumeController(
                maxVolume = 10,
                startedAtMillis = 0L,
                rampDurationMillis = 240_000L
            )

        assertEquals(5, controller.targetVolume(120_000L))
        assertEquals(10, controller.targetVolume(240_000L))
        controller.onIntermediateTaskCompleted(240_000L)
        assertEquals(5, controller.targetVolume(360_000L))
        assertEquals(10, controller.targetVolume(480_000L))
    }

    @Test
    fun verifiedProgressFadesLinearlyAndNeverRegresses() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 0L)

        assertEquals(10, controller.targetVolume(60_000L))
        assertEquals(7, controller.onVerifiedProgress(0.25f, 60_000L))
        assertEquals(5, controller.onVerifiedProgress(0.5f, 61_000L))
        assertEquals(5, controller.onVerifiedProgress(0.25f, 62_000L))
        assertEquals(0, controller.onVerifiedProgress(1f, 63_000L))
    }

    @Test
    fun inactivityStartsFreshRampFromReducedVolume() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 0L)
        controller.onVerifiedProgress(0.5f, 60_000L)

        assertEquals(5, controller.targetVolume(65_000L))
        assertEquals(5, controller.targetVolume(65_001L))
        assertEquals(7, controller.targetVolume(95_000L))
        assertEquals(10, controller.targetVolume(125_000L))
    }

    @Test
    fun repeatedProgressDoesNotRefreshInactivityWindow() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 0L)
        controller.onVerifiedProgress(0.5f, 60_000L)

        assertEquals(5, controller.onVerifiedProgress(0.5f, 64_000L))
        assertEquals(10, controller.targetVolume(125_000L))
    }

    @Test
    fun progressAfterInactivityFadesCurrentVolumeAcrossRemainingProgress() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 0L)
        controller.onVerifiedProgress(0.5f, 60_000L)

        assertEquals(7, controller.targetVolume(95_000L))
        assertEquals(3, controller.onVerifiedProgress(0.75f, 95_000L))
        assertEquals(0, controller.onVerifiedProgress(1f, 96_000L))
    }

    @Test
    fun intermediateTaskResetsToZeroAndStartsFreshRamp() {
        val controller = AlarmVolumeController(maxVolume = 10, startedAtMillis = 0L)

        assertEquals(0, controller.onIntermediateTaskCompleted(30_000L))
        assertEquals(0, controller.targetVolume(30_000L))
        assertEquals(5, controller.targetVolume(60_000L))
        assertEquals(10, controller.targetVolume(90_000L))
    }
}
