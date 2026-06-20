package com.example.smartalarmer.puzzle

interface MathPuzzleProvider {
    fun generate(difficulty: Difficulty): MathPuzzle
}

interface TypingPuzzleProvider {
    fun getRandomQuote(): String
    fun isMatch(target: String, input: String): Boolean
}

interface MemoryPuzzleProvider {
    fun generateSequence(length: Int): List<Int>
    fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean
}
