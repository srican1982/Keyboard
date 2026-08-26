package com.personal.sinhalakeyboard

import android.content.Context

class EnglishSuggestions(context: Context) {

    private val words: List<String>

    init {
        words = loadWords(context)
    }

    private fun loadWords(context: Context): List<String> {
        return try {
            context.assets.open("english_words.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.length in 2..12 && it.all { c -> c.isLetter() } }
                    .distinct()
                    .sorted()
                    .take(25_000)
                    .toList()
            }
        } catch (_: Exception) {
            FALLBACK_WORDS
        }
    }

    fun suggest(prefix: String, limit: Int = 8): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val results = linkedSetOf<SuggestionCandidate>()

        // Binary search for prefix range in sorted word list
        val start = words.binarySearch(lower).let { idx ->
            if (idx >= 0) idx else -(idx + 1)
        }
        var count = 0
        for (i in start until words.size) {
            val word = words[i]
            if (!word.startsWith(lower)) break
            if (word != lower) {
                val display = word.replaceFirstChar { c ->
                    if (p[0].isUpperCase()) c.uppercaseChar() else c
                }
                results.add(SuggestionCandidate(display, display))
                count++
                if (count >= limit) break
            }
        }

        return results.toList()
    }

    companion object {
        private val FALLBACK_WORDS = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for",
            "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his",
            "by", "from", "they", "we", "say", "her", "she", "or", "an", "will", "my",
            "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
            "about", "who", "get", "which", "go", "me", "when", "make", "can", "like",
            "time", "no", "just", "him", "know", "take", "people", "into", "year",
            "your", "good", "some", "could", "them", "see", "other", "than", "then",
            "now", "look", "only", "come", "its", "over", "think", "also", "back",
            "hello", "thanks", "please", "sorry", "yes", "okay", "great", "really",
            "because", "something", "everything", "nothing", "someone", "anyone",
            "message", "tomorrow", "yesterday", "morning", "beautiful", "important",
        )
    }
}
