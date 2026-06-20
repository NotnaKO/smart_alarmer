package com.example.smartalarmer.puzzle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingEngineTest {
    @Test
    fun testMatchTextExact() {
        val target = "Wake up and smell the coffee!"
        assertTrue(TypingEngine.isMatch(target, "Wake up and smell the coffee!"))
        assertTrue(TypingEngine.isMatch(target, "  Wake up and smell the coffee!  "))
        assertFalse(TypingEngine.isMatch(target, "wake up and smell the coffee!"))
    }

    @Test
    fun testGetRandomQuote() {
        val quote1 = TypingEngine.getRandomQuote()
        val quote2 = TypingEngine.getRandomQuote()
        // Ensure non-empty
        assertTrue(quote1.isNotEmpty())
        assertTrue(quote2.isNotEmpty())
    }
}
