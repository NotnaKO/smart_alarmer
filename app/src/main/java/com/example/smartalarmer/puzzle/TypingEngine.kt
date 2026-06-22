package com.example.smartalarmer.puzzle

object TypingEngine : TypingPuzzleProvider {
    override fun getRandomQuote(quotes: List<String>): String = quotes.random()

    override fun isMatch(target: String, input: String): Boolean {
        return target.trim() == input.trim()
    }
}
