package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Fast Singlish -> Sinhala suggestion engine.
 *
 * Performance design:
 *
 * 1. Exact dictionary lookup is O(1).
 * 2. Dictionary prefix lookup uses a sorted index + binary search.
 * 3. No full dictionary scan on every keypress.
 * 4. No SQLite lookup inside a dictionary result loop.
 * 5. Corpus searches are limited to the strongest prefixes.
 * 6. Frequency lookups are batched.
 * 7. Ambiguity expansion is only one generation.
 */
class SinglishEngine(
    context: Context,
    private val personalHistory: PersonalHistoryDatabase? = null,
    private val typingMemory: TypingMemory? = null,
) {

    /**
     * Exact Roman -> Sinhala mappings.
     */
    private val dictionary =
        HashMap<String, String>()

    /**
     * Roman dictionary frequency.
     */
    private val frequency =
        HashMap<String, Int>()

    /**
     * Sorted Roman keys.
     *
     * Used for fast prefix searching without scanning the whole dictionary.
     */
    private var sortedDictionaryKeys:
        List<String> = emptyList()

    private val corpusDb =
        SinhalaFrequencyDatabase(context)

    init {
        loadDictionary(context)

        /*
         * Build this once.
         *
         * Prefix lookup can now binary-search this list instead of scanning
         * every dictionary entry for every typed character.
         */
        sortedDictionaryKeys =
            dictionary.keys.sorted()
    }

    fun close() {
        corpusDb.close()
    }

    /**
     * Final word conversion when the user presses space without selecting
     * a suggestion.
     */
    fun transliterate(
        input: String,
    ): String {

        val typed =
            input.trim()

        if (typed.isEmpty()) {
            return ""
        }

        val lower =
            typed.lowercase()

        /*
         * Exact known mapping first.
         */
        dictionary[lower]
            ?.let {
                return it
            }

        /*
         * Exact personal learned mapping second.
         */
        typingMemory
            ?.exactSinhalaEntry(typed)
            ?.let { entry ->

                if (
                    containsSinhalaScript(
                        entry.value
                    )
                ) {
                    return entry.value
                }
            }

        return SinglishConverter.convert(
            typed
        )
    }

    /**
     * Extremely cheap live conversion.
     *
     * No database access.
     */
    fun transliterateLive(
        input: String,
    ): String {

        val typed =
            input.trim()

        if (typed.isEmpty()) {
            return ""
        }

        return SinglishConverter.convert(
            typed
        )
    }

    /**
     * Generate live Sinhala suggestions.
     */
    fun sinhalaSuggestions(
        prefix: String,
        limit: Int = 12,
    ): List<SuggestionCandidate> {

        val typed =
            prefix.trim()

        if (
            typed.isEmpty() ||
            limit <= 0
        ) {
            return emptyList()
        }

        val lower =
            typed.lowercase()

        /*
         * ================================================================
         * Candidate source groups
         * ================================================================
         */

        val directReadings =
            linkedSetOf<String>()

        val dictionaryExactReadings =
            linkedSetOf<String>()

        val variantReadings =
            linkedSetOf<String>()

        val personalExactReadings =
            linkedSetOf<String>()

        val homophoneReadings =
            linkedSetOf<String>()

        /*
         * Strong prefixes come from:
         *
         * - direct conversion
         * - exact dictionary mapping
         * - exact personal mapping
         *
         * These are searched first.
         */
        val strongSinhalaPrefixes =
            linkedSetOf<String>()

        /*
         * Variant prefixes are weaker.
         *
         * We deliberately limit these.
         */
        val variantSinhalaPrefixes =
            linkedSetOf<String>()

        /*
         * ================================================================
         * 1. Direct conversion
         * ================================================================
         */

        val directReading =
            SinglishConverter.convert(
                typed
            )

        if (
            isValidSinhalaWord(
                directReading
            )
        ) {

            directReadings.add(
                directReading
            )

            homophoneReadings.add(
                directReading
            )

            collectStrongPrefixes(
                reading = directReading,
                out = strongSinhalaPrefixes,
            )
        }

        /*
         * ================================================================
         * 2. Exact dictionary mapping
         * ================================================================
         */

        dictionary[lower]
            ?.takeIf {
                isValidSinhalaWord(it)
            }
            ?.let { word ->

                dictionaryExactReadings.add(
                    word
                )

                homophoneReadings.add(
                    word
                )

                collectStrongPrefixes(
                    reading = word,
                    out = strongSinhalaPrefixes,
                )
            }

        /*
         * ================================================================
         * 3. Exact personal mapping
         * ================================================================
         */

        typingMemory
            ?.exactSinhalaEntry(
                typed
            )
            ?.let { entry ->

                val word =
                    entry.value

                if (
                    isValidSinhalaWord(
                        word
                    )
                ) {

                    personalExactReadings.add(
                        word
                    )

                    homophoneReadings.add(
                        word
                    )

                    collectStrongPrefixes(
                        reading = word,
                        out = strongSinhalaPrefixes,
                    )
                }
            }

        /*
         * ================================================================
         * 4. Controlled phonetic ambiguity
         * ================================================================
         *
         * SinglishAmbiguityVariants already returns its own alternatives.
         *
         * We only consume a limited number for real-time prediction.
         */

        val romanVariants =
            SinglishAmbiguityVariants
                .liveVariants(typed)
                .asSequence()
                .filter {
                    it.isNotBlank() &&
                        !it.equals(
                            typed,
                            ignoreCase = false,
                        )
                }
                .take(
                    MAX_ROMAN_VARIANTS
                )
                .toList()

        for (roman in romanVariants) {

            val readings =
                AlternateSinhalaReadings
                    .forRoman(roman)

            for (reading in readings) {

                if (
                    !isValidSinhalaWord(
                        reading
                    )
                ) {
                    continue
                }

                variantReadings.add(
                    reading
                )

                homophoneReadings.add(
                    reading
                )

                /*
                 * For ambiguity-derived readings we only search the exact
                 * reading as a prefix.
                 *
                 * We do NOT drop characters from every variant.
                 */
                if (
                    variantSinhalaPrefixes.size <
                    MAX_VARIANT_PREFIXES
                ) {

                    variantSinhalaPrefixes.add(
                        reading
                    )
                }
            }

            if (
                variantSinhalaPrefixes.size >=
                MAX_VARIANT_PREFIXES
            ) {
                break
            }
        }

        /*
         * ================================================================
         * 5. Build the final small corpus-prefix set
         * ================================================================
         */

        val corpusPrefixes =
            linkedSetOf<String>()

        /*
         * Strong prefixes always get priority.
         */
        strongSinhalaPrefixes
            .forEach {

                if (
                    corpusPrefixes.size <
                    MAX_CORPUS_PREFIXES
                ) {

                    corpusPrefixes.add(
                        it
                    )
                }
            }

        /*
         * Only then use ambiguity prefixes.
         */
        variantSinhalaPrefixes
            .forEach {

                if (
                    corpusPrefixes.size <
                    MAX_CORPUS_PREFIXES
                ) {

                    corpusPrefixes.add(
                        it
                    )
                }
            }

        /*
         * ================================================================
         * 6. Corpus search
         * ================================================================
         */

        val corpusFrequencies =
            LinkedHashMap<String, Int>()

        if (
            corpusPrefixes.isNotEmpty()
        ) {

            val prefixResults =
                corpusDb
                    .queryMergedByPrefixes(
                        prefixes =
                            corpusPrefixes,

                        limitPerPrefix =
                            CORPUS_RESULTS_PER_PREFIX,

                        totalLimit =
                            MAX_CORPUS_RESULTS,
                    )

            for (
                entry in prefixResults
            ) {

                if (
                    !isValidSinhalaWord(
                        entry.word
                    )
                ) {
                    continue
                }

                corpusFrequencies[
                    entry.word
                ] =
                    maxOf(
                        corpusFrequencies[
                            entry.word
                        ] ?: 0,

                        entry.frequency,
                    )
            }
        }

        /*
         * ================================================================
         * 7. Exact frequency lookup for source candidates
         * ================================================================
         *
         * One batch query instead of many individual SQLite queries.
         */

        val sourceWords =
            linkedSetOf<String>()

        sourceWords.addAll(
            directReadings
        )

        sourceWords.addAll(
            dictionaryExactReadings
        )

        sourceWords.addAll(
            personalExactReadings
        )

        sourceWords.addAll(
            variantReadings
        )

        if (
            sourceWords.isNotEmpty()
        ) {

            val sourceFrequencies =
                corpusDb.lookupFrequencies(
                    sourceWords
                )

            for (
                (word, freq) in
                sourceFrequencies
            ) {

                if (freq <= 0) {
                    continue
                }

                corpusFrequencies[word] =
                    maxOf(
                        corpusFrequencies[word]
                            ?: 0,

                        freq,
                    )
            }
        }

        /*
         * ================================================================
         * 8. Fast Roman dictionary prefix search
         * ================================================================
         *
         * OLD:
         *
         * dictionary.entries.filter { startsWith(...) }
         *
         * That scanned the entire dictionary.
         *
         * NEW:
         *
         * binary-search the sorted Roman-key list and read only matching
         * entries.
         */

        val dictionaryPrefixMatches =
            findDictionaryPrefixMatches(
                romanPrefix = lower,
                limit =
                    MAX_DICTIONARY_PREFIX_MATCHES,
            )

        if (
            dictionaryPrefixMatches
                .isNotEmpty()
        ) {

            val dictionaryWords =
                linkedSetOf<String>()

            for (
                match in
                dictionaryPrefixMatches
            ) {

                if (
                    isValidSinhalaWord(
                        match.sinhala
                    )
                ) {

                    dictionaryWords.add(
                        match.sinhala
                    )
                }
            }

            /*
             * One batched corpus lookup.
             *
             * No N+1 SQLite queries.
             */
            val frequencies =
                corpusDb.lookupFrequencies(
                    dictionaryWords
                )

            for (
                match in
                dictionaryPrefixMatches
            ) {

                val sinhala =
                    match.sinhala

                if (
                    !isValidSinhalaWord(
                        sinhala
                    )
                ) {
                    continue
                }

                val corpusFrequency =
                    frequencies[sinhala]
                        ?: 0

                val weight =
                    if (
                        corpusFrequency > 0
                    ) {
                        corpusFrequency
                    } else {
                        match.frequency
                    }

                corpusFrequencies[sinhala] =
                    maxOf(
                        corpusFrequencies[
                            sinhala
                        ] ?: 0,

                        weight,
                    )
            }
        }

        /*
         * ================================================================
         * 9. Personal prefix suggestions
         * ================================================================
         */

        val memorySuggestions =
            typingMemory
                ?.sinhalaSuggestions(
                    prefix = typed,
                    limit =
                        MAX_MEMORY_SUGGESTIONS,
                )
                .orEmpty()

        /*
         * ================================================================
         * 10. Candidate pool for personal-history lookup
         * ================================================================
         */

        val candidateWords =
            linkedSetOf<String>()

        candidateWords.addAll(
            dictionaryExactReadings
        )

        candidateWords.addAll(
            personalExactReadings
        )

        candidateWords.addAll(
            directReadings
        )

        candidateWords.addAll(
            variantReadings
        )

        candidateWords.addAll(
            corpusFrequencies.keys
        )

        for (
            candidate in
            memorySuggestions
        ) {

            val word =
                candidate
                    .commitText
                    .trim()

            if (
                isValidSinhalaWord(
                    word
                )
            ) {

                candidateWords.add(
                    word
                )
            }
        }

        /*
         * ================================================================
         * 11. Personal history
         * ================================================================
         */

        val personalCounts =
            if (
                candidateWords.isEmpty()
            ) {

                emptyMap()

            } else {

                personalHistory
                    ?.getCounts(
                        words =
                            candidateWords,

                        langMode =
                            PersonalHistoryDatabase
                                .MODE_SINHALA,
                    )
                    .orEmpty()
            }

        /*
         * ================================================================
         * 12. Rank
         * ================================================================
         */

        val ranked =
            SinhalaSuggestionRanker
                .rank(
                    typedRomanLength =
                        typed.length,

                    corpusFrequencies =
                        corpusFrequencies,

                    personalCounts =
                        personalCounts,

                    homophoneReadings =
                        homophoneReadings,

                    limit =
                        limit,

                    directReadings =
                        directReadings,

                    dictionaryExactReadings =
                        dictionaryExactReadings,

                    variantReadings =
                        variantReadings,

                    personalExactReadings =
                        personalExactReadings,
                )

        /*
         * ================================================================
         * 13. UI candidates
         * ================================================================
         */

        return ranked.map { word ->

            val personal =
                word in personalExactReadings ||
                    (personalCounts[word] ?: 0) > 0

            SuggestionCandidate(
                display = word,
                commitText = word,
                isPersonal = personal,
            )
        }
    }

    /**
     * Strong source prefixes.
     *
     * Direct/dictionary/personal readings may have one relaxed prefix.
     */
    private fun collectStrongPrefixes(
        reading: String,
        out: MutableSet<String>,
    ) {

        if (
            !isValidSinhalaWord(
                reading
            )
        ) {
            return
        }

        out.add(
            reading
        )

        /*
         * Only relax reasonably long Sinhala strings.
         */
        if (
            reading.length >= 5
        ) {

            out.add(
                reading.dropLast(1)
            )
        }
    }

    /**
     * Fast dictionary prefix lookup.
     *
     * sortedDictionaryKeys is alphabetically ordered.
     *
     * Binary search finds the first possible matching Roman key.
     * From there we only walk the contiguous matching region.
     */
    private fun findDictionaryPrefixMatches(
        romanPrefix: String,
        limit: Int,
    ): List<DictionaryMatch> {

        if (
            romanPrefix.isBlank() ||
            sortedDictionaryKeys.isEmpty() ||
            limit <= 0
        ) {
            return emptyList()
        }

        val start =
            lowerBound(
                sortedDictionaryKeys,
                romanPrefix,
            )

        if (
            start >=
            sortedDictionaryKeys.size
        ) {
            return emptyList()
        }

        val out =
            ArrayList<DictionaryMatch>(
                limit
            )

        var index =
            start

        while (
            index <
            sortedDictionaryKeys.size &&
            out.size <
            limit
        ) {

            val key =
                sortedDictionaryKeys[index]

            /*
             * Because the list is sorted, once startsWith stops matching,
             * we're outside this prefix region.
             */
            if (
                !key.startsWith(
                    romanPrefix
                )
            ) {
                break
            }

            val sinhala =
                dictionary[key]

            if (
                sinhala != null &&
                isValidSinhalaWord(
                    sinhala
                )
            ) {

                out.add(
                    DictionaryMatch(
                        roman =
                            key,

                        sinhala =
                            sinhala,

                        frequency =
                            frequency[key]
                                ?: 1,
                    )
                )
            }

            index++
        }

        return out
    }

    /**
     * Standard lower-bound binary search.
     *
     * Finds the first value >= target.
     */
    private fun lowerBound(
        list: List<String>,
        target: String,
    ): Int {

        var low =
            0

        var high =
            list.size

        while (
            low < high
        ) {

            val mid =
                (low + high)
                    ushr 1

            if (
                list[mid] <
                target
            ) {

                low =
                    mid + 1

            } else {

                high =
                    mid
            }
        }

        return low
    }

    /**
     * Load assets/sinhala_dict.txt
     *
     * Format:
     *
     * roman|sinhala|frequency
     */
    private fun loadDictionary(
        context: Context,
    ) {

        try {

            context.assets
                .open(
                    "sinhala_dict.txt"
                )
                .bufferedReader()
                .useLines { lines ->

                    lines.forEach { line ->

                        val trimmed =
                            line.trim()

                        if (
                            trimmed.isEmpty() ||
                            trimmed.startsWith("#")
                        ) {
                            return@forEach
                        }

                        val parts =
                            trimmed.split(
                                "|",
                                limit = 3,
                            )

                        if (
                            parts.size < 2
                        ) {
                            return@forEach
                        }

                        val roman =
                            parts[0]
                                .trim()
                                .lowercase()

                        val sinhala =
                            parts[1]
                                .trim()

                        val freq =
                            parts
                                .getOrNull(2)
                                ?.trim()
                                ?.toIntOrNull()
                                ?: 1

                        if (
                            roman.isBlank() ||
                            !isValidSinhalaWord(
                                sinhala
                            )
                        ) {
                            return@forEach
                        }

                        dictionary[roman] =
                            sinhala

                        frequency[roman] =
                            freq
                    }
                }

        } catch (_: Exception) {

            /*
             * Converter + Sinhala corpus can still operate without the
             * optional Roman dictionary.
             */
        }
    }

    private fun isValidSinhalaWord(
        text: String,
    ): Boolean {

        if (
            text.isBlank() ||
            text.contains(' ')
        ) {
            return false
        }

        return containsSinhalaScript(
            text
        )
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any { char ->

            char.code in
                0x0D80..0x0DFF
        }
    }

    private data class DictionaryMatch(
        val roman: String,
        val sinhala: String,
        val frequency: Int,
    )

    private companion object {

        /**
         * Keep live ambiguity bounded.
         *
         * We don't need dozens of weak Roman spellings for every keypress.
         */
        const val MAX_ROMAN_VARIANTS =
            8

        /**
         * Only the best ambiguity-derived Sinhala prefixes go to SQLite.
         */
        const val MAX_VARIANT_PREFIXES =
            4

        /**
         * Maximum total SQLite prefix searches per suggestion request.
         *
         * This is one of the most important performance controls.
         */
        const val MAX_CORPUS_PREFIXES =
            6

        /**
         * Number of words retrieved for each corpus prefix.
         */
        const val CORPUS_RESULTS_PER_PREFIX =
            12

        /**
         * Overall corpus candidate cap.
         */
        const val MAX_CORPUS_RESULTS =
            48

        /**
         * Maximum dictionary prefix candidates.
         */
        const val MAX_DICTIONARY_PREFIX_MATCHES =
            24

        /**
         * Personal-memory suggestions.
         */
        const val MAX_MEMORY_SUGGESTIONS =
            6
    }
}
