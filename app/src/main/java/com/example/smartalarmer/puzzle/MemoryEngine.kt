package com.example.smartalarmer.puzzle

object MemoryEngine {
    fun generateSequence(length: Int): List<Int> {
        return List(length) { (0..8).random() }
    }

    fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean {
        if (userInputs.size > sequence.size) return false
        for (i in userInputs.indices) {
            if (sequence[i] != userInputs[i]) return false
        }
        return true
    }
}
