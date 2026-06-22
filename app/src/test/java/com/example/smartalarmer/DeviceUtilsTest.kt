package com.example.smartalarmer

import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceUtilsTest {
    @Test
    fun testIsMiUiOnNonXiaomiDvm() {
        // In standard host JVM environment, SystemProperties does not exist
        // and should safely return false.
        assertFalse(DeviceUtils.isMiUi())
    }
}
