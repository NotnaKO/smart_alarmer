package com.example.smartalarmer

import com.example.smartalarmer.service.AlarmService
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmServiceTest {
    @Test
    fun testCalculateGradualVolume() {
        val maxVolume = 7

        // Start of playback (0s) -> minimum volume 1
        assertEquals(1, AlarmService.calculateGradualVolume(0L, maxVolume))

        // Halfway through (15s) -> 1 + 15 * 6 / 30 = 4
        assertEquals(4, AlarmService.calculateGradualVolume(15L, maxVolume))

        // End of crescendo (30s) -> max volume 7
        assertEquals(7, AlarmService.calculateGradualVolume(30L, maxVolume))

        // Beyond crescendo (45s) -> max volume 7
        assertEquals(7, AlarmService.calculateGradualVolume(45L, maxVolume))
    }
}
