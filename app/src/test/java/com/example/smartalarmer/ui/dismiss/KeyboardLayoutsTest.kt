package com.example.smartalarmer.ui.dismiss

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutsTest {
    @Test
    fun testKeyboardLayoutResolution() {
        val ruLayout = KeyboardLayouts.getLayoutForLanguage("ru")
        assertEquals('й', ruLayout[0][0])
        assertEquals('э', ruLayout[1].last())

        val deLayout = KeyboardLayouts.getLayoutForLanguage("de")
        assertEquals('z', deLayout[0][5])
        assertEquals('ü', deLayout[0].last())

        val esLayout = KeyboardLayouts.getLayoutForLanguage("es")
        assertEquals('ñ', esLayout[1].last())

        val enLayout = KeyboardLayouts.getLayoutForLanguage("en")
        assertEquals('y', enLayout[0][5])
        assertEquals('l', enLayout[1].last())
    }
}
