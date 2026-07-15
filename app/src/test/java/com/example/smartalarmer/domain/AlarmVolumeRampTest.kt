package com.example.smartalarmer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmVolumeRampTest {
    @Test
    fun supportedPresetsArePreservedAndMalformedValuesUseDefault() {
        AlarmVolumeRamp.OPTIONS_SECONDS.forEach { seconds ->
            assertEquals(seconds, AlarmVolumeRamp.sanitize(seconds))
        }
        assertEquals(AlarmVolumeRamp.DEFAULT_SECONDS, AlarmVolumeRamp.sanitize(0))
        assertEquals(AlarmVolumeRamp.DEFAULT_SECONDS, AlarmVolumeRamp.sanitize(90))
    }
}
