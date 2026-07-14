package com.example.smartalarmer

import com.example.smartalarmer.utils.DeviceUtils
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceUtilsTest {
    @Test
    fun testIsXiaomiOnNonAndroidHost() {
        assertFalse(DeviceUtils.isXiaomi())
    }
}
