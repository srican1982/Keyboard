package com.personal.sinhalakeyboard

import kotlin.math.ln

/**
 * Ranks Sinhala suggestions using linguistic relevance first,
 * personal learning second, and corpus frequency third.
 *
 * IMPORTANT:
 * A mechanically generated Sinhala form is NOT automatically assumed
 * to be the correct word.
 *
 * Example:
 *
 * Roman: patiyo
 *
 * If:
 *   පැටියෝ = dictionary/corpus-supported candidate
 *   පටියෝ   = only a mechanical converter output
 *
 * then පැටියෝ should be able to rank above පටියෝ.
 */
object SinhalaSuggestionRanker {

    /*
     * Source bonuses.
     *
     * Dictionary-supported words are strongest because they represent an
     * explicit known Roman -> Sinhala mapping.
     *
     * Exact personal mappings are also strong, but not permanently unbeatable.
     */
    private const val DICTIONARY_EXACT_BONUS = 1_500_000

    private const val PERSONAL_EXACT_BONUS = 1_200_000

    private const val DIRECT_READING_BONUS = 650_000

    private const val VARIANT_READING_BONUS = 350_000

    /*
     * Personal usage of the Sinhala word itself.
     */
    private const val PERSONAL_COUNT_WEIGHT = 80_000

    /**
     * Old arguments remain so existing callers continue to compile.
     *
     * New optional arguments allow SinglishEngine to tell us WHERE each
     * candidate came from.
     */
    fun rank(
        typedRomanLength: Int,
        corpusFrequencies: Map<String, Int>,
        personalCounts: Map<String, Int>,
        homophoneReadings: Collection<String>,
        limit: Int,

        // New source-aware inputs.
        directReadings: Collection<String> = emptyList(),
        dictionaryExactReadings: Collection<String> = emptyList(),
        variantReadings: Collection<String> = emptyList(),
        personalExactReadings: Collection<String> = emptyList(),
    ): List<String> {

        if (limit <= 0) {
            return emptyList()
        }

        val homophoneSet =
            homophoneReadings.toHashSet()

        val directSet =
            directReadings.toHashSet()

        val dictionarySet =
            dictionaryExactReadings.toHashSet()

        val variantSet =
            variantReadings.toHashSet()

        val personalExactSet =
            personalExactReadings.toHashSet()

        /*
         * Candidate pool.
         *
         * Include every source so valid zero-frequency candidates are
         * not accidentally discarded.
         */
        val candidateWords =
            linkedSetOf<String>()

        candidateWords.addAll(dictionaryExactReadings)
        candidateWords.addAll(personalExactReadings)
        candidateWords.addAll(directReadings)
        candidateWords.addAll(variantReadings)
        candidateWords.addAll(homophoneReadings)
        candidateWords.addAll(personalCounts.keys)
        candidateWords.addAll(corpusFrequencies.keys)

        val ranked =
            ArrayList<RankedCandidate>()

        for (word in candidateWords) {

            if (word.isBlank()) {
                continue
            }

            if (word.contains(' ')) {
                continue
            }

            if (!containsSinhalaScript(word)) {
                continue
            }

            val fromCorpus =
                word in corpusFrequencies

            if (
                !SinhalaSuggestionRules
                    .isReasonableSinhalaSuggestion(
                        sinhala = word,
                        typedRomanLength = typedRomanLength,
                        fromCorpus = fromCorpus,
                    )
            ) {
                continue
            }

            val corpusFrequency =
                corpusFrequencies[word] ?: 0

            val personalCount =
                personalCounts[word] ?: 0

            val isDictionaryExact =
                word in dictionarySet

            val isPersonalExact =
                word in personalExactSet

            val isDirect =
                word in directSet

            val isVariant =
                word in variantSet

            val isHomophone =
                word in homophoneSet

            /*
             * Linguistic/source relevance.
             *
             * Notice:
             * direct conversion gets a useful bonus,
             * but dictionary/corpus evidence can still beat it.
             */
            var relevanceScore = 0

            if (isDictionaryExact) {
                relevanceScore +=
                    DICTIONARY_EXACT_BONUS
            }

            if (isPersonalExact) {
                relevanceScore +=
                    PERSONAL_EXACT_BONUS
            }

            if (isDirect) {
                relevanceScore +=
                    DIRECT_READING_BONUS
            }

            if (isVariant) {
                relevanceScore +=
                    VARIANT_READING_BONUS
            }

            /*
             * Backward compatibility.
             *
             * Until SinglishEngine is updated, old homophone readings still
             * receive some relevance so existing behavior doesn't collapse.
             */
            if (
                isHomophone &&
                !isDirect &&
                !isVariant &&
                !isDictionaryExact
            ) {
                relevanceScore +=
                    VARIANT_READING_BONUS
            }

            /*
             * Personal history is strong but does not automatically override
             * all linguistic evidence.
             */
            val personalScore =
                personalCount *
                    PERSONAL_COUNT_WEIGHT

            /*
             * Compress corpus frequency.
             *
             * Very common Sinhala words remain preferred over rare words,
             * but raw million-level frequency values cannot dominate
             * everything else.
             */
            val corpusScore =
                logarithmicCorpusScore(
                    corpusFrequency
                )

            /*
             * Corpus existence itself is useful evidence that this is an
             * actual attested Sinhala word.
             */
            val corpusExistenceBonus =
                if (corpusFrequency > 0) {
                    120_000
                } else {
                    0
                }

            val totalScore =
                relevanceScore +
                    personalScore +
                    corpusScore +
                    corpusExistenceBonus

            /*
             * Reject candidates with absolutely no evidence.
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
                    dictionaryExact = isDictionaryExact,
                    personalExact = isPersonalExact,
                    direct = isDirect,
                    variant = isVariant,
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
                     * Strongest tie-breakers.
                     */
                    .thenByDescending {
                        it.dictionaryExact
                    }

                    .thenByDescending {
                        it.personalExact
                    }

                    .thenByDescending {
                        it.direct
                    }

                    .thenByDescending {
                        it.variant
                    }

                    .thenByDescending {
                        it.personalCount
                    }

                    .thenByDescending {
                        it.corpusFrequency
                    }

                    /*
                     * Only weak final tie-breakers.
                     */
                    .thenBy {
                        lengthDistance(
                            sinhalaLength =
                                it.word.length,

                            typedRomanLength =
                                typedRomanLength,
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
     * Converts huge raw frequencies into a manageable score.
     */
    private fun logarithmicCorpusScore(
        frequency: Int,
    ): Int {

        if (frequency <= 0) {
            return 0
        }

        return (
            ln(
                frequency.toDouble() +
                    1.0
            ) * 1000.0
        ).toInt()
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any {
            it.code in 0x0D80..0x0DFF
        }
    }

    /**
     * Only a weak tie-breaker.
     *
     * Sinhala Unicode length and Roman input length do not have a reliable
     * one-to-one relationship.
     */
    private fun lengthDistance(
        sinhalaLength: Int,
        typedRomanLength: Int,
    ): Int {

        val expected =
            (typedRomanLength * 1.15)
                .toInt()
                .coerceIn(
                    1,
                    48,
                )

        return kotlin.math.abs(
            sinhalaLength -
                expected
        )
    }

    private data class RankedCandidate(

        val word: String,

        val totalScore: Int,

        val dictionaryExact: Boolean,

        val personalExact: Boolean,

        val direct: Boolean,

        val variant: Boolean,

        val personalCount: Int,

        val corpusFrequency: Int,
    )
}
