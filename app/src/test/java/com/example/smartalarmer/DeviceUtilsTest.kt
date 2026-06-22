package com.example.smartalarmer

import com.example.smartalarmer.utils.DeviceUtils
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceUtilsTest {
    @Test
    fun testIsMiUiOnNonXiaomiDvm() {
        // In standard host JVM environment, SystemProperties does not exist
        // and should safely return false.
        assertFalse(DeviceUtils.isMiUi())
        assertFalse(DeviceUtils.isXiaomi())
        assertFalse(DeviceUtils.isHyperOs())
    }
}
