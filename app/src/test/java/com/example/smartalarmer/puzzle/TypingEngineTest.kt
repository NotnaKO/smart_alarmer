package com.example.smartalarmer.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingEngineTest {
    @Test
    fun testNormalizationAndMatching() {
        // English matches
        assertTrue(TypingEngine.isMatch("The early bird gets the worm.", "the early bird gets the worm"))
        assertTrue(TypingEngine.isMatch("No snooze allowed. Rise and shine!", "no snooze allowed rise and shine"))

        // Spanish matches (accent-insensitive)
        assertTrue(TypingEngine.isMatch("Al que madruga, Dios le ayuda.", "al que madruga dios le ayuda"))
        assertTrue(TypingEngine.isMatch("No se permite dormir más.", "no se permite dormir mas"))

        // German matches
        assertTrue(TypingEngine.isMatch("Morgenstund hat Gold im Mund.", "morgenstund hat gold im mund"))

        // Russian matches (punctuation-tolerant)
        assertTrue(TypingEngine.isMatch("Кто рано встает, тому Бог подает.", "кто рано встает тому бог подает"))
        assertTrue(TypingEngine.isMatch("Ранний подъем — первый шаг к успеху.", "ранний подъем первый шаг к успеху"))

        // Non-matches
        assertFalse(TypingEngine.isMatch("The early bird", "The late bird"))
    }

    @Test
    fun progressOnlyCountsNormalizedCorrectPrefix() {
        assertEquals(2f / 12f, TypingEngine.progress("Ál que vuela!", "al "), 0.001f)
        assertEquals(0f, TypingEngine.progress("Wake up now", "Wrong"), 0.001f)
        assertEquals(1f, TypingEngine.progress("Wake up now!", "wake up now"), 0.001f)
    }
}
