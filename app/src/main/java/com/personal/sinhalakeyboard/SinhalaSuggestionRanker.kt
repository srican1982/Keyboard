package com.personal.sinhalakeyboard

import kotlin.math.ln

/**
 * Scores and orders Sinhala suggestion chips.
 *
 * Ranking philosophy:
 *
 * 1. Direct / homophone readings should rank very strongly.
 * 2. Personal usage should strongly influence ranking.
 * 3. Corpus frequency should help, but must not dominate.
 * 4. Length is only a final tie-breaker.
 */
object SinhalaSuggestionRanker {

    private const val HOMOPHONE_BONUS = 1_000_000
    private const val PERSONAL_COUNT_WEIGHT = 100_000

    fun rank(
        typedRomanLength: Int,
        corpusFrequencies: Map<String, Int>,
        personalCounts: Map<String, Int>,
        homophoneReadings: Collection<String>,
        limit: Int,
    ): List<String> {

        if (limit <= 0) {
            return emptyList()
        }

        /*
         * Preserve lookup efficiency.
         */
        val homophoneSet = homophoneReadings.toHashSet()

        /*
         * Candidate pool.
         *
         * Direct/homophone readings are intentionally inserted first.
         */
        val candidateWords = linkedSetOf<String>()

        candidateWords.addAll(homophoneReadings)
        candidateWords.addAll(personalCounts.keys)
        candidateWords.addAll(corpusFrequencies.keys)

        val ranked = ArrayList<RankedCandidate>()

        for (word in candidateWords) {

            if (word.isBlank()) {
                continue
            }

            /*
             * Suggestions should be a single word during live completion.
             */
            if (word.contains(' ')) {
                continue
            }

            if (!containsSinhalaScript(word)) {
                continue
            }

            val fromCorpus = word in corpusFrequencies

            if (
                !SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                    sinhala = word,
                    typedRomanLength = typedRomanLength,
                    fromCorpus = fromCorpus,
                )
            ) {
                continue
            }

            val personalCount =
                personalCounts[word] ?: 0

            val corpusFrequency =
                corpusFrequencies[word] ?: 0

            val isHomophone =
                word in homophoneSet

            /*
             * IMPORTANT:
             *
             * Raw corpus frequency is NOT used directly.
             *
             * A corpus word with frequency 2,000,000 should not beat an exact
             * phonetic reading purely because it is globally common.
             */
            val corpusScore =
                logarithmicCorpusScore(corpusFrequency)

            /*
             * Personal history is intentionally strong.
             *
             * If the user repeatedly chooses the same Sinhala word,
             * that preference should become obvious quickly.
             */
            val personalScore =
                personalCount * PERSONAL_COUNT_WEIGHT

            /*
             * Direct / homophone forms get a strong relevance bonus.
             *
             * This prevents the correct transliteration from disappearing
             * merely because another broad corpus completion is common.
             */
            val relevanceScore =
                if (isHomophone) {
                    HOMOPHONE_BONUS
                } else {
                    0
                }

            val totalScore =
                relevanceScore +
                    personalScore +
                    corpusScore

            /*
             * Unlike the old ranker, zero-frequency homophones are NOT
             * filtered out.
             *
             * A direct transliteration is still useful even if it does not
             * exist in the corpus.
             */
            if (
                totalScore <= 0 &&
                !isHomophone
            ) {
                continue
            }

            ranked.add(
                RankedCandidate(
                    word = word,
                    totalScore = totalScore,
                    isHomophone = isHomophone,
                    personalCount = personalCount,
                    corpusFrequency = corpusFrequency,
                )
            )
        }

        return ranked
            .sortedWith(
                compareByDescending<RankedCandidate> {
                    it.totalScore
                }
                    /*
                     * If two words happen to have the same score,
                     * prefer the direct/homophone reading.
                     */
                    .thenByDescending {
                        it.isHomophone
                    }
                    /*
                     * Then prefer what the user has personally selected.
                     */
                    .thenByDescending {
                        it.personalCount
                    }
                    /*
                     * Then use raw corpus frequency only as a tie-break.
                     */
                    .thenByDescending {
                        it.corpusFrequency
                    }
                    /*
                     * Sinhala Unicode length is only a weak final tie-breaker.
                     */
                    .thenBy {
                        lengthDistance(
                            sinhalaLength = it.word.length,
                            typedRomanLength = typedRomanLength,
                        )
                    }
                    .thenBy {
                        it.word.length
                    }
                    .thenBy {
                        it.word
                    },
            )
            .take(limit)
            .map {
                it.word
            }
    }

    /**
     * Compress very large corpus-frequency differences.
     *
     * Example approximate values:
     *
     * frequency = 10        -> ~2,397
     * frequency = 100       -> ~4,615
     * frequency = 10,000    -> ~9,210
     * frequency = 1,000,000 -> ~13,815
     *
     * So globally common words still get rewarded, but cannot overwhelm
     * phonetic relevance.
     */
    private fun logarithmicCorpusScore(
        frequency: Int,
    ): Int {

        if (frequency <= 0) {
            return 0
        }

        return (
            ln(frequency.toDouble() + 1.0) * 1000.0
        ).toInt()
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any { char ->
            char.code in 0x0D80..0x0DFF
        }
    }

    /**
     * Keep this only as a weak tie-breaker.
     *
     * Roman character count and Sinhala Unicode length do not map perfectly,
     * so this should never strongly influence ranking.
     */
    private fun lengthDistance(
        sinhalaLength: Int,
        typedRomanLength: Int,
    ): Int {

        val expected =
            (typedRomanLength * 1.15)
                .toInt()
                .coerceIn(
                    minimumValue = 1,
                    maximumValue = 48,
                )

        return kotlin.math.abs(
            sinhalaLength - expected
        )
    }

    private data class RankedCandidate(
        val word: String,
        val totalScore: Int,
        val isHomophone: Boolean,
        val personalCount: Int,
        val corpusFrequency: Int,
    )
}
