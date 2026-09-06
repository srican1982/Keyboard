package com.personal.sinhalakeyboard

/**
 * Scores and orders Sinhala suggestion chips:
 * score = personal_count * 10_000 + corpus_frequency
 */
object SinhalaSuggestionRanker {

    fun rank(
        typedRomanLength: Int,
        corpusFrequencies: Map<String, Int>,
        personalCounts: Map<String, Int>,
        homophoneReadings: Collection<String>,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()

        val scores = HashMap<String, Int>()
        val candidateWords = LinkedHashSet<String>()
        candidateWords.addAll(corpusFrequencies.keys)
        candidateWords.addAll(personalCounts.keys)
        candidateWords.addAll(homophoneReadings)

        for (word in candidateWords) {
            if (word.isBlank() || word.contains(' ')) continue
            if (!containsSinhalaScript(word)) continue
            if (!SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                    word,
                    typedRomanLength,
                    fromCorpus = word in corpusFrequencies,
                )
            ) {
                continue
            }
            val personal = personalCounts[word] ?: 0
            val corpus = corpusFrequencies[word] ?: 0
            val score = PersonalHistoryDatabase.PERSONAL_WEIGHT * personal + corpus
            if (score > 0 || homophoneReadings.contains(word)) {
                scores[word] = score
            }
        }

        val hasCorpusOrPersonal = scores.values.any { it > 0 }

        return scores.entries
            .asSequence()
            .filter { (_, score) -> score > 0 || !hasCorpusOrPersonal }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { lengthDistance(it.key.length, typedRomanLength) }
                    .thenBy { it.key.length }
                    .thenBy { it.key },
            )
            .take(limit)
            .map { it.key }
            .toList()
    }

    private fun containsSinhalaScript(text: String): Boolean =
        text.any { it.code in 0x0D80..0x0DFF }

    private fun lengthDistance(sinhalaLength: Int, typedRomanLength: Int): Int {
        val expected = (typedRomanLength * 1.15).toInt().coerceIn(1, 48)
        return kotlin.math.abs(sinhalaLength - expected)
    }
}
