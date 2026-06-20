package com.example.smartalarmer.puzzle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEngineTest {
    @Test
    fun testGenerateSequence() {
        val seq = MemoryEngine.generateSequence(5)
        assertTrue(seq.size == 5)
        assertTrue(seq.all { it in 0..8 })
    }

    @Test
    fun testVerifyStep() {
        val sequence = listOf(1, 4, 3, 8)
        
        // Correct steps
        assertTrue(MemoryEngine.verifyStep(sequence, listOf(1)))
        assertTrue(MemoryEngine.verifyStep(sequence, listOf(1, 4)))
        assertTrue(MemoryEngine.verifyStep(sequence, listOf(1, 4, 3, 8)))

        // Incorrect steps
        assertFalse(MemoryEngine.verifyStep(sequence, listOf(2)))
        assertFalse(MemoryEngine.verifyStep(sequence, listOf(1, 3)))
        assertFalse(MemoryEngine.verifyStep(sequence, listOf(1, 4, 3, 8, 0)))
    }
}
