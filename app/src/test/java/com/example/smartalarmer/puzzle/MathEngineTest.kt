package com.example.smartalarmer.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathEngineTest {
    @Test
    fun testGenerateEasyEquation() {
        val puzzle = MathEngine.generate(Difficulty.EASY)
        assertEquals(Difficulty.EASY, puzzle.difficulty)
        assertTrue(puzzle.equation.contains("+") || puzzle.equation.contains("-"))
        // Test that evaluating works or simply check answer range
        assertTrue(puzzle.answer in 0..200)
    }

    @Test
    fun testGenerateMediumEquation() {
        val puzzle = MathEngine.generate(Difficulty.MEDIUM)
        assertEquals(Difficulty.MEDIUM, puzzle.difficulty)
        assertTrue(puzzle.equation.contains("*"))
        assertTrue(puzzle.equation.contains("+"))
    }

    @Test
    fun testGenerateHardEquation() {
        val puzzle = MathEngine.generate(Difficulty.HARD)
        assertEquals(Difficulty.HARD, puzzle.difficulty)
        assertTrue(puzzle.equation.contains("x"))
    }
}
