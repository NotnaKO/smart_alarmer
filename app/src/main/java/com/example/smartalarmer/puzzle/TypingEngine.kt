package com.example.smartalarmer.puzzle

object TypingEngine : TypingPuzzleProvider {
    override fun getRandomQuote(quotes: List<String>): String = quotes.random()

    override fun progress(
        target: String,
        input: String
    ): Float {
        val normalizedTarget = normalize(target)
        val normalizedInput = normalize(input)
        if (normalizedInput.isEmpty() || !normalizedTarget.startsWith(normalizedInput)) return 0f
        return (normalizedInput.length.toFloat() / normalizedTarget.length.toFloat()).coerceIn(0f, 1f)
    }

    override fun isMatch(
        target: String,
        input: String
    ): Boolean = normalize(target) == normalize(input)

    private fun normalize(str: String): String {
        // 1. Convert to lowercase
        var normalized = str.trim().lowercase()

        // 2. Remove accents/diacritics (e.g. á -> a, ü -> u)
        normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFD)
        normalized = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

        // 3. Keep only letters and spaces (stripping punctuation like commas, periods, dashes, quotes, etc.)
        normalized = normalized.replace("[^\\p{L}\\s]".toRegex(), "")

        // 4. Collapse multiple spaces into one
        return normalized.replace("\\s+".toRegex(), " ").trim()
    }
}
