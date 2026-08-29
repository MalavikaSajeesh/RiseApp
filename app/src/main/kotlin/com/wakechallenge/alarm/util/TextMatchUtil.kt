package com.wakechallenge.alarm.util

object TextMatchUtil {

    private fun normalize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /**
     * Word-overlap similarity between a target phrase and what speech-to-text produced.
     * Robust to ASR mistakes better than a strict edit-distance match on the full string,
     * since a few missed/garbled words shouldn't fail the whole recitation.
     * Returns a value in 0.0..1.0.
     */
    fun similarity(target: String, spoken: String): Double {
        val targetWords = normalize(target)
        val spokenWords = normalize(spoken).toMutableList()
        if (targetWords.isEmpty()) return 0.0

        var matched = 0
        for (word in targetWords) {
            val idx = spokenWords.indexOfFirst { levenshteinRatio(it, word) > 0.8 }
            if (idx >= 0) {
                matched++
                spokenWords.removeAt(idx)
            }
        }
        return matched.toDouble() / targetWords.size
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1.0 else 1.0 - dist.toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
