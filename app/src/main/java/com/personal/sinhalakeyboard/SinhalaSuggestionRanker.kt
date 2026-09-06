package com.personal.sinhalakeyboard

/**
 * Scores and orders Sinhala suggestion chips using corpus frequency as the primary signal.
 */
object SinhalaSuggestionRanker {

    /** Personal history outranks corpus, but corpus beats unverified converter guesses. */
    private const val PERSONAL_BASE = 20_000_000

    fun rank(
        typedRomanLength: Int,
        personal: List<SuggestionCandidate>,
        corpusFrequencies: Map<String, Int>,
        homophoneReadings: Collection<String>,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()

        val scores = HashMap<String, Int>()

        personal.forEachIndexed { index, candidate ->
            val word = candidate.commitText
            if (word.isBlank() || word.contains(' ')) return@forEachIndexed
            val boost = PERSONAL_BASE + (personal.size - index) * 1_000
            scores[word] = maxOf(scores[word] ?: 0, boost)
        }

        for ((word, freq) in corpusFrequencies) {
            if (freq <= 0) continue
            if (!SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                    word,
                    typedRomanLength,
                    fromCorpus = true,
                )
            ) {
                continue
            }
            scores[word] = maxOf(scores[word] ?: 0, freq)
        }

        for (reading in homophoneReadings) {
            if (reading.isBlank() || reading.contains(' ')) continue
            if (!SinhalaSuggestionRules.isReasonableSinhalaSuggestion(reading, typedRomanLength)) continue
            if (reading !in scores) {
                scores[reading] = corpusFrequencies[reading] ?: 0
            }
        }

        val hasCorpusHits = scores.values.any { it in 1 until PERSONAL_BASE }

        return scores.entries
            .asSequence()
            .filter { (word, score) ->
                score >= PERSONAL_BASE ||
                    score > 0 ||
                    (!hasCorpusHits && homophoneReadings.contains(word))
            }
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

    private fun lengthDistance(sinhalaLength: Int, typedRomanLength: Int): Int {
        val expected = (typedRomanLength * 1.15).toInt().coerceIn(1, 48)
        return kotlin.math.abs(sinhalaLength - expected)
    }
}
