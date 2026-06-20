package com.example.smartalarmer.puzzle

object TypingEngine : TypingPuzzleProvider {
    private val quotes = listOf(
        "The early bird gets the worm.",
        "Waking up is the first step to success.",
        "No snooze allowed. Rise and shine!",
        "Make today count. Get out of bed."
    )

    override fun getRandomQuote(): String = quotes.random()

    override fun isMatch(target: String, input: String): Boolean {
        return target.trim() == input.trim()
    }
}
