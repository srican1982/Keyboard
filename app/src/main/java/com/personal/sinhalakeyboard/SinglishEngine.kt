package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Singlish IME engine:
 *
 * - direct Roman -> Sinhala conversion
 * - controlled ambiguity generation
 * - dictionary lookup
 * - corpus prefix completion
 * - personal learning
 * - source-aware ranking
 *
 * Sinhala mode only.
 */
class SinglishEngine(
    context: Context,
    private val personalHistory: PersonalHistoryDatabase? = null,
    private val typingMemory: TypingMemory? = null,
) {

    private val dictionary: MutableMap<String, String> = mutableMapOf()
    private val frequency: MutableMap<String, Int> = mutableMapOf()

    private val corpusDb = SinhalaFrequencyDatabase(context)

    init {
        loadDictionary(context)
    }

    fun close() {
        corpusDb.close()
    }

    /**
     * Final transliteration when the user commits the word without choosing
     * a suggestion.
     *
     * Exact dictionary mapping wins.
     * Otherwise fall back to the rule-based converter.
     */
    fun transliterate(input: String): String {
        val word = input.trim()

        if (word.isEmpty()) {
            return ""
        }

        val lower = word.lowercase()

        dictionary[lower]?.let { exact ->
            return exact
        }

        return SinglishConverter.convert(word)
    }

    /**
     * Cheap live conversion.
     */
    fun transliterateLive(input: String): String {
        val word = input.trim()

        if (word.isEmpty()) {
            return ""
        }

        return SinglishConverter.convert(word)
    }

    /**
     * Sinhala live suggestions.
     */
    fun sinhalaSuggestions(
        prefix: String,
        limit: Int = 12,
    ): List<SuggestionCandidate> {

        val typed = prefix.trim()

        if (typed.isEmpty()) {
            return emptyList()
        }

        val lower = typed.lowercase()

        /*
         * ============================================================
         * SOURCE GROUPS
         * ============================================================
         *
         * We intentionally keep these separate so the ranker knows
         * where each suggestion came from.
         */

        val directReadings = linkedSetOf<String>()
        val dictionaryExactReadings = linkedSetOf<String>()
        val variantReadings = linkedSetOf<String>()
        val personalExactReadings = linkedSetOf<String>()

        /*
         * All phonetic/readable candidates for backward compatibility.
         */
        val homophoneReadings = linkedSetOf<String>()

        /*
         * Sinhala prefixes used for corpus completion.
         */
        val sinhalaPrefixes = linkedSetOf<String>()

        /*
         * ============================================================
         * 1. DIRECT MECHANICAL CONVERSION
         * ============================================================
         */

        val directReading =
            SinglishConverter.convert(typed)

        if (containsSinhalaScript(directReading)) {

            directReadings.add(directReading)
            homophoneReadings.add(directReading)

            collectSinhalaPrefixes(
                reading = directReading,
                out = sinhalaPrefixes,
            )
        }

        /*
         * ============================================================
         * 2. EXACT DICTIONARY MAPPING
         * ============================================================
         *
         * This is stronger than the raw converter because it represents
         * a known Roman -> Sinhala mapping.
         */

        dictionary[lower]
            ?.takeIf {
                containsSinhalaScript(it)
            }
            ?.let { exactDictionaryWord ->

                dictionaryExactReadings.add(
                    exactDictionaryWord
                )

                homophoneReadings.add(
                    exactDictionaryWord
                )

                collectSinhalaPrefixes(
                    reading = exactDictionaryWord,
                    out = sinhalaPrefixes,
                )
            }

        /*
         * ============================================================
         * 3. EXACT PERSONAL LEARNED MAPPING
         * ============================================================
         *
         * Example:
         *
         * user repeatedly chose:
         *   patiyo -> පැටියෝ
         *
         * This should strongly influence ranking.
         */

        typingMemory
            ?.exactSinhalaEntry(typed)
            ?.let { learned ->

                val word =
                    learned.value

                if (containsSinhalaScript(word)) {

                    personalExactReadings.add(word)
                    homophoneReadings.add(word)

                    collectSinhalaPrefixes(
                        reading = word,
                        out = sinhalaPrefixes,
                    )
                }
            }

        /*
         * ============================================================
         * 4. CONTROLLED ROMAN AMBIGUITY
         * ============================================================
         *
         * Only ONE ambiguity expansion.
         */

        val romanVariants =
            romanSearchVariants(typed)

        /*
         * Skip the exact typed form here because it was already handled
         * separately as the direct reading.
         */
        for (roman in romanVariants) {

            if (roman.equals(typed, ignoreCase = false)) {
                continue
            }

            val readings =
                AlternateSinhalaReadings.forRoman(roman)

            for (reading in readings) {

                if (!containsSinhalaScript(reading)) {
                    continue
                }

                variantReadings.add(reading)
                homophoneReadings.add(reading)

                collectSinhalaPrefixes(
                    reading = reading,
                    out = sinhalaPrefixes,
                )
            }
        }

        /*
         * ============================================================
         * 5. CORPUS PREFIX COMPLETIONS
         * ============================================================
         */

        val corpusFrequencies =
            LinkedHashMap<String, Int>()

        val prefixEntries =
            corpusDb.queryMergedByPrefixes(
                prefixes = sinhalaPrefixes,
                limitPerPrefix = 18,
                totalLimit = 72,
            )

        for (entry in prefixEntries) {

            if (!containsSinhalaScript(entry.word)) {
                continue
            }

            corpusFrequencies[entry.word] =
                maxOf(
                    corpusFrequencies[entry.word] ?: 0,
                    entry.frequency,
                )
        }

        /*
         * Exact source readings may get pushed out of a prefix query by
         * higher-frequency completions, so explicitly look them up.
         */

        val sourceReadings =
            linkedSetOf<String>()

        sourceReadings.addAll(directReadings)
        sourceReadings.addAll(dictionaryExactReadings)
        sourceReadings.addAll(variantReadings)
        sourceReadings.addAll(personalExactReadings)

        val exactFrequencies =
            corpusDb.lookupFrequencies(
                sourceReadings
            )

        for ((word, freq) in exactFrequencies) {

            if (!containsSinhalaScript(word)) {
                continue
            }

            corpusFrequencies[word] =
                maxOf(
                    corpusFrequencies[word] ?: 0,
                    freq,
                )
        }

        /*
         * ============================================================
         * 6. ROMAN DICTIONARY PREFIX COMPLETIONS
         * ============================================================
         *
         * Only startsWith.
         *
         * No old subsequence fuzzy matcher.
         */

        addDictionaryCorpusMatches(
            lower = lower,
            corpusFrequencies = corpusFrequencies,
        )

        /*
         * ============================================================
         * 7. GENERIC PERSONAL PREFIX MEMORY
         * ============================================================
         */

        val memorySuggestions =
            typingMemory
                ?.sinhalaSuggestions(
                    prefix = typed,
                    limit = 8,
                )
                .orEmpty()

        /*
         * Candidate pool for PersonalHistory lookup.
         */
        val candidateWords =
            linkedSetOf<String>()

        candidateWords.addAll(dictionaryExactReadings)
        candidateWords.addAll(personalExactReadings)
        candidateWords.addAll(directReadings)
        candidateWords.addAll(variantReadings)
        candidateWords.addAll(corpusFrequencies.keys)

        for (candidate in memorySuggestions) {

            val word =
                candidate.commitText

            if (containsSinhalaScript(word)) {
                candidateWords.add(word)
            }
        }

        /*
         * ============================================================
         * 8. PERSONAL WORD FREQUENCY
         * ============================================================
         */

        val personalCounts =
            personalHistory
                ?.getCounts(
                    words = candidateWords,
                    langMode =
                        PersonalHistoryDatabase.MODE_SINHALA,
                )
                .orEmpty()

        /*
         * ============================================================
         * 9. FINAL SOURCE-AWARE RANKING
         * ============================================================
         */

        val ranked =
            SinhalaSuggestionRanker.rank(

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
         * ============================================================
         * 10. UI SUGGESTION OBJECTS
         * ============================================================
         */

        return ranked.map { word ->

            val isPersonalWord =
                (personalCounts[word] ?: 0) > 0 ||
                    word in personalExactReadings

            SuggestionCandidate(
                display = word,
                commitText = word,
                isPersonal = isPersonalWord,
            )
        }
    }

    /**
     * ONE-LEVEL Roman ambiguity only.
     */
    private fun romanSearchVariants(
        roman: String,
    ): Set<String> {

        val variants =
            linkedSetOf<String>()

        variants.add(roman)

        variants.addAll(
            SinglishAmbiguityVariants
                .liveVariants(roman)
        )

        return variants
    }

    /**
     * Strong Sinhala corpus prefixes.
     *
     * Full reading always.
     *
     * One-character relaxation only for longer words.
     */
    private fun collectSinhalaPrefixes(
        reading: String,
        out: MutableSet<String>,
    ) {

        if (reading.isBlank()) {
            return
        }

        out.add(reading)

        if (reading.length >= 5) {

            out.add(
                reading.dropLast(1)
            )
        }
    }

    /**
     * Dictionary prefix completions.
     *
     * Live prediction uses startsWith only.
     */
    private fun addDictionaryCorpusMatches(
        lower: String,
        corpusFrequencies: MutableMap<String, Int>,
    ) {

        if (lower.isBlank()) {
            return
        }

        dictionary.entries
            .asSequence()
            .filter { (romanKey, _) ->

                romanKey.startsWith(lower)
            }
            .forEach { (romanKey, sinhala) ->

                if (!containsSinhalaScript(sinhala)) {
                    return@forEach
                }

                val dictionaryWeight =
                    frequency[romanKey] ?: 1

                val realCorpusFrequency =
                    corpusDb.lookupFrequency(
                        sinhala
                    )

                val weight =
                    if (realCorpusFrequency > 0) {
                        realCorpusFrequency
                    } else {
                        dictionaryWeight
                    }

                corpusFrequencies[sinhala] =
                    maxOf(
                        corpusFrequencies[sinhala] ?: 0,
                        weight,
                    )
            }
    }

    /**
     * Load assets/sinhala_dict.txt
     *
     * Expected:
     *
     * roman|sinhala|frequency
     */
    private fun loadDictionary(
        context: Context,
    ) {

        try {

            context.assets
                .open("sinhala_dict.txt")
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

                        if (parts.size < 2) {
                            return@forEach
                        }

                        val key =
                            parts[0]
                                .trim()
                                .lowercase()

                        val value =
                            parts[1]
                                .trim()

                        val freq =
                            parts
                                .getOrNull(2)
                                ?.trim()
                                ?.toIntOrNull()
                                ?: 1

                        if (
                            key.isNotBlank() &&
                            value.isNotBlank()
                        ) {

                            dictionary[key] =
                                value

                            frequency[key] =
                                freq
                        }
                    }
                }

        } catch (_: Exception) {

            /*
             * Dictionary is optional enhancement.
             *
             * SinglishConverter + corpus can still function.
             */
        }
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any { char ->

            char.code in
                0x0D80..0x0DFF
        }
    }
}
