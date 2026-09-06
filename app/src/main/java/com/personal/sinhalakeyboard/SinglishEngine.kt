package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Singlish IME engine:
 *
 * 1. Converts Roman/Singlish input to Sinhala.
 * 2. Generates a controlled set of spelling alternatives.
 * 3. Looks up Sinhala corpus completions.
 * 4. Adds personal-history / learned suggestions.
 * 5. Sends the final candidate set to SinhalaSuggestionRanker.
 *
 * Sinhala mode only.
 * English mode must use [EnglishSuggestions].
 */
class SinglishEngine(
    context: Context,
    private val personalHistory: PersonalHistoryDatabase? = null,
    private val typingMemory: TypingMemory? = null,
) {

    /**
     * Roman key -> Sinhala word.
     *
     * Example:
     *
     * "kohomada" -> "කොහොමද"
     */
    private val dictionary: MutableMap<String, String> = mutableMapOf()

    /**
     * Roman dictionary-key frequency.
     *
     * This comes from sinhala_dict.txt.
     */
    private val frequency: MutableMap<String, Int> = mutableMapOf()

    /**
     * Large Sinhala word-frequency corpus.
     */
    private val corpusDb = SinhalaFrequencyDatabase(context)

    init {
        loadDictionary(context)
    }

    fun close() {
        corpusDb.close()
    }

    /**
     * Final transliteration used when a word is committed.
     *
     * Prefer an exact dictionary mapping when available.
     * Otherwise use the rule-based converter.
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
     * Cheap rule-based conversion used while typing.
     */
    fun transliterateLive(input: String): String {
        val word = input.trim()

        if (word.isEmpty()) {
            return ""
        }

        return SinglishConverter.convert(word)
    }

    /**
     * Generate Sinhala suggestions for a partially typed Roman/Singlish word.
     *
     * Important design:
     *
     * - Ambiguity is expanded only ONE level.
     * - AlternateSinhalaReadings does NOT expand ambiguity again.
     * - Corpus searches use strong Sinhala prefixes first.
     * - Very broad dropLast(2) prefix searching is intentionally avoided.
     * - Loose subsequence fuzzy matching is not used for live prediction.
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
         * Roman forms that we will consider.
         *
         * The exact typed form is always first.
         */
        val romanVariants = romanSearchVariants(typed)

        /*
         * All reasonable Sinhala readings produced from the Roman forms.
         *
         * LinkedHashSet preserves insertion order and removes duplicates.
         */
        val homophoneReadings = linkedSetOf<String>()

        /*
         * Sinhala prefixes used to search the large frequency corpus.
         */
        val sinhalaPrefixes = linkedSetOf<String>()

        /*
         * Explicitly calculate the exact typed conversion first.
         *
         * This ensures the direct reading is always represented even if
         * future ambiguity code changes.
         */
        val directReading = SinglishConverter.convert(typed)

        if (containsSinhalaScript(directReading)) {
            homophoneReadings.add(directReading)

            collectSinhalaPrefixes(
                reading = directReading,
                out = sinhalaPrefixes,
            )
        }

        /*
         * Convert each Roman spelling into Sinhala readings.
         *
         * IMPORTANT:
         * romanSearchVariants() already handles ambiguity.
         *
         * AlternateSinhalaReadings must therefore only convert the supplied
         * Roman form and apply its very small trailing-vowel fallback.
         */
        for (roman in romanVariants) {

            val readings = AlternateSinhalaReadings.forRoman(roman)

            for (reading in readings) {

                if (!containsSinhalaScript(reading)) {
                    continue
                }

                homophoneReadings.add(reading)

                collectSinhalaPrefixes(
                    reading = reading,
                    out = sinhalaPrefixes,
                )
            }
        }

        /*
         * Sinhala word -> corpus frequency.
         */
        val corpusFrequencies = LinkedHashMap<String, Int>()

        /*
         * Find words beginning with our generated Sinhala prefixes.
         *
         * We intentionally use smaller result sets than before because
         * relevance is more valuable than generating huge candidate pools.
         */
        val prefixEntries = corpusDb.queryMergedByPrefixes(
            prefixes = sinhalaPrefixes,
            limitPerPrefix = 18,
            totalLimit = 72,
        )

        for (entry in prefixEntries) {

            if (!containsSinhalaScript(entry.word)) {
                continue
            }

            corpusFrequencies[entry.word] = maxOf(
                corpusFrequencies[entry.word] ?: 0,
                entry.frequency,
            )
        }

        /*
         * Exact Sinhala readings may not appear in the prefix result if the
         * prefix query limit was reached by more frequent words.
         *
         * Always look up their exact frequencies separately.
         */
        val exactReadingFrequencies =
            corpusDb.lookupFrequencies(homophoneReadings)

        for ((word, freq) in exactReadingFrequencies) {

            if (!containsSinhalaScript(word)) {
                continue
            }

            corpusFrequencies[word] = maxOf(
                corpusFrequencies[word] ?: 0,
                freq,
            )
        }

        /*
         * Add sinhala_dict.txt prefix matches.
         *
         * IMPORTANT:
         * Live suggestion mode now uses true startsWith() matching only.
         *
         * The previous fuzzy subsequence matcher could match unrelated words.
         */
        addDictionaryCorpusMatches(
            lower = lower,
            corpusFrequencies = corpusFrequencies,
        )

        /*
         * Complete candidate pool.
         */
        val candidateWords = linkedSetOf<String>()

        /*
         * Exact/direct readings are intentionally inserted first.
         */
        candidateWords.addAll(homophoneReadings)

        /*
         * Then corpus completions.
         */
        candidateWords.addAll(corpusFrequencies.keys)

        /*
         * Add words previously learned for this Roman prefix.
         */
        typingMemory
            ?.sinhalaSuggestions(
                prefix = typed,
                limit = 8,
            )
            ?.forEach { candidate ->

                val word = candidate.commitText

                if (containsSinhalaScript(word)) {
                    candidateWords.add(word)
                }
            }

        /*
         * Retrieve personal usage counts only for words that are actually
         * candidates for this request.
         */
        val personalCounts = personalHistory
            ?.getCounts(
                words = candidateWords,
                langMode = PersonalHistoryDatabase.MODE_SINHALA,
            )
            .orEmpty()

        /*
         * Final ranking.
         *
         * We will improve SinhalaSuggestionRanker in the NEXT step.
         */
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = typed.length,
            corpusFrequencies = corpusFrequencies,
            personalCounts = personalCounts,
            homophoneReadings = homophoneReadings.filter {
                containsSinhalaScript(it)
            },
            limit = limit,
        )

        /*
         * Convert ranked Sinhala strings into UI candidates.
         */
        return ranked.map { word ->

            val personal =
                (personalCounts[word] ?: 0) > 0

            SuggestionCandidate(
                display = word,
                commitText = word,
                isPersonal = personal,
            )
        }
    }

    /**
     * Generate a controlled ONE-LEVEL set of Roman spelling alternatives.
     *
     * OLD behavior:
     *
     * typed
     *   -> variants
     *   -> variants of variants
     *
     * That generated too many weak spellings.
     *
     * NEW behavior:
     *
     * typed
     *   -> direct ambiguity variants only
     */
    private fun romanSearchVariants(
        roman: String,
    ): Set<String> {

        val variants = linkedSetOf<String>()

        /*
         * Always keep the exact user spelling first.
         */
        variants.add(roman)

        /*
         * One ambiguity expansion only.
         */
        variants.addAll(
            SinglishAmbiguityVariants.liveVariants(roman)
        )

        return variants
    }

    /**
     * Add useful Sinhala corpus prefixes.
     *
     * We always search the full generated reading.
     *
     * For sufficiently long readings we also remove ONE final Unicode
     * character to support live partial-word completion.
     *
     * We intentionally DO NOT use dropLast(2), because it produced overly
     * broad searches and pulled in unrelated high-frequency words.
     */
    private fun collectSinhalaPrefixes(
        reading: String,
        out: MutableSet<String>,
    ) {
        if (reading.isBlank()) {
            return
        }

        /*
         * Strongest prefix.
         */
        out.add(reading)

        /*
         * Slightly relaxed prefix only for longer readings.
         */
        if (reading.length >= 5) {
            out.add(
                reading.dropLast(1)
            )
        }
    }

    /**
     * Add suggestions from sinhala_dict.txt.
     *
     * Only true Roman-prefix matching is allowed during live typing.
     *
     * Example:
     *
     * typed = "pati"
     *
     * accepts:
     *   patiya
     *   patiyo
     *
     * does not accept unrelated dictionary keys merely because p,a,t,i
     * appear somewhere in the same order.
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

                /*
                 * Prefer the true corpus frequency when available.
                 *
                 * Fall back to the dictionary's own weight otherwise.
                 */
                val corpusFrequency =
                    corpusDb.lookupFrequency(sinhala)

                val weight =
                    if (corpusFrequency > 0) {
                        corpusFrequency
                    } else {
                        dictionaryWeight
                    }

                corpusFrequencies[sinhala] = maxOf(
                    corpusFrequencies[sinhala] ?: 0,
                    weight,
                )
            }
    }

    /**
     * Load Roman -> Sinhala entries from assets/sinhala_dict.txt.
     *
     * Expected format:
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

                        val trimmed = line.trim()

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
                            parts[1].trim()

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
                            dictionary[key] = value
                            frequency[key] = freq
                        }
                    }
                }

        } catch (_: Exception) {

            /*
             * sinhala_dict.txt is an enhancement.
             *
             * The keyboard can still work using:
             *
             * SinglishConverter
             * +
             * SinhalaFrequencyDatabase.
             */
        }
    }

    private fun containsSinhalaScript(
        text: String,
    ): Boolean {

        return text.any { char ->
            char.code in 0x0D80..0x0DFF
        }
    }
}
