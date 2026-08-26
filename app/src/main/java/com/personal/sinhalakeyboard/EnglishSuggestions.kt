package com.personal.sinhalakeyboard

object EnglishSuggestions {

    private val words = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for",
        "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his",
        "by", "from", "they", "we", "say", "her", "she", "or", "an", "will", "my",
        "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
        "about", "who", "get", "which", "go", "me", "when", "make", "can", "like",
        "time", "no", "just", "him", "know", "take", "people", "into", "year",
        "your", "good", "some", "could", "them", "see", "other", "than", "then",
        "now", "look", "only", "come", "its", "over", "think", "also", "back",
        "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most",
        "us", "hello", "hi", "hey", "thanks", "thank", "please", "sorry", "yes",
        "okay", "ok", "great", "nice", "love", "happy", "help", "need", "want",
        "going", "doing", "been", "being", "had", "has", "did", "was", "were",
        "are", "is", "am", "been", "very", "much", "more", "still", "here",
        "where", "why", "today", "tomorrow", "yesterday", "morning", "night",
        "friend", "family", "home", "house", "phone", "message", "text", "call",
        "email", "school", "office", "work", "job", "money", "food", "water",
        "please", "welcome", "beautiful", "awesome", "perfect", "right", "wrong",
        "true", "false", "maybe", "sure", "really", "actually", "always", "never",
        "sometimes", "already", "again", "another", "something", "nothing",
        "everything", "someone", "anyone", "everyone", "nothing", "world",
        "country", "city", "street", "name", "number", "question", "answer",
        "problem", "solution", "idea", "plan", "start", "stop", "finish", "wait",
        "tell", "ask", "talk", "speak", "listen", "read", "write", "learn",
        "teach", "play", "run", "walk", "drive", "eat", "drink", "sleep",
        "wake", "buy", "sell", "pay", "send", "receive", "open", "close",
        "find", "lose", "keep", "leave", "stay", "move", "change", "try",
        "hope", "wish", "feel", "believe", "remember", "forget", "understand",
        "explain", "mean", "matter", "important", "different", "same", "best",
        "better", "worse", "bad", "big", "small", "long", "short", "high",
        "low", "old", "young", "hot", "cold", "fast", "slow", "easy", "hard",
        "free", "busy", "ready", "late", "early", "soon", "later", "before",
        "during", "while", "until", "since", "between", "through", "across",
        "around", "near", "far", "inside", "outside", "above", "below",
        "together", "alone", "maybe", "probably", "definitely", "certainly",
    )

    fun suggest(prefix: String, limit: Int = 6): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val results = linkedSetOf<SuggestionCandidate>()

        words.filter { it.startsWith(lower) && it != lower }
            .sortedWith(compareBy<String> { it.length }.thenBy { it })
            .take(limit)
            .forEach { word ->
                val display = word.replaceFirstChar { c ->
                    if (p[0].isUpperCase()) c.uppercaseChar() else c
                }
                results.add(SuggestionCandidate(display, display))
            }

        return results.toList()
    }
}
