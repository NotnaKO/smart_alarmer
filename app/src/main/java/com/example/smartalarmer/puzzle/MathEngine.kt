package com.example.smartalarmer.puzzle

enum class Difficulty { EASY, MEDIUM, HARD }
data class MathPuzzle(val equation: String, val answer: Int, val difficulty: Difficulty)

object MathEngine {
    fun generate(difficulty: Difficulty): MathPuzzle {
        return when (difficulty) {
            Difficulty.EASY -> {
                val a = (10..99).random()
                val b = (10..99).random()
                val isPlus = (0..1).random() == 1
                if (isPlus) {
                    MathPuzzle("$a + $b", a + b, difficulty)
                } else {
                    val max = maxOf(a, b)
                    val min = minOf(a, b)
                    MathPuzzle("$max - $min", max - min, difficulty)
                }
            }
            Difficulty.MEDIUM -> {
                val a = (2..12).random()
                val b = (2..12).random()
                val c = (10..99).random()
                MathPuzzle("$a * $b + $c", (a * b) + c, difficulty)
            }
            Difficulty.HARD -> {
                val x = (2..9).random()
                val a = (3..9).random()
                val b = (10..40).random()
                val c = a * x + b
                MathPuzzle("$a * x + $b = $c", x, difficulty)
            }
        }
    }
}
