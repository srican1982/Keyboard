package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Singlish IME engine: phonetic conversion plus frequency-ranked Sinhala suggestions.
 * Sinhala mode only — English mode must use [EnglishSuggestions].
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

    private fun loadDictionary(context: Context) {
        try {
            context.assets.open("sinhala_dict.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    val parts = trimmed.split("|", limit = 3)
                    if (parts.size >= 2) {
                        val key = parts[0].lowercase()
                        val value = parts[1]
                        val freq = parts.getOrNull(2)?.toIntOrNull() ?: 1
                        if (key.isNotBlank() && value.isNotBlank()) {
                            dictionary[key] = value
                            frequency[key] = freq
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // rules-only fallback
        }
    }

    fun transliterate(input: String): String {
        val word = input.trim()
        if (word.isEmpty()) return ""
        dictionary[word.lowercase()]?.let { return it }
        return SinglishConverter.convert(word)
    }

    fun transliterateLive(input: String): String = SinglishConverter.convert(input.trim())

    /** Sinhala-mode chips only — Sinhala Unicode script, ranked by personal history + corpus. */
    fun sinhalaSuggestions(prefix: String, limit: Int = 12): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val romanVariants = romanSearchVariants(p)
        val homophoneReadings = linkedSetOf<String>()
        val sinhalaPrefixes = linkedSetOf<String>()

        for (roman in romanVariants) {
            for (reading in AlternateSinhalaReadings.forRoman(roman)) {
                if (!containsSinhalaScript(reading)) continue
                homophoneReadings.add(reading)
                collectSinhalaPrefixes(reading, sinhalaPrefixes)
            }
        }

        val corpusFrequencies = LinkedHashMap<String, Int>()
        for (entry in corpusDb.queryMergedByPrefixes(sinhalaPrefixes, limitPerPrefix = 24, totalLimit = 96)) {
            if (!containsSinhalaScript(entry.word)) continue
            corpusFrequencies[entry.word] = maxOf(corpusFrequencies[entry.word] ?: 0, entry.frequency)
        }
        for ((word, freq) in corpusDb.lookupFrequencies(homophoneReadings)) {
            if (!containsSinhalaScript(word)) continue
            corpusFrequencies[word] = maxOf(corpusFrequencies[word] ?: 0, freq)
        }
        addDictionaryCorpusMatches(lower, corpusFrequencies)

        val candidateWords = LinkedHashSet<String>()
        candidateWords.addAll(corpusFrequencies.keys)
        candidateWords.addAll(homophoneReadings)

        typingMemory?.sinhalaSuggestions(p, limit = 6)?.forEach { candidate ->
            if (containsSinhalaScript(candidate.commitText)) {
                candidateWords.add(candidate.commitText)
            }
        }

        val personalCounts = personalHistory?.getCounts(
            candidateWords,
            PersonalHistoryDatabase.MODE_SINHALA,
        ).orEmpty()

        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = p.length,
            corpusFrequencies = corpusFrequencies,
            personalCounts = personalCounts,
            homophoneReadings = homophoneReadings.filter { containsSinhalaScript(it) },
            limit = limit,
        )

        return ranked.map { word ->
            val personal = (personalCounts[word] ?: 0) > 0
            SuggestionCandidate(word, word, isPersonal = personal)
        }
    }

    private fun romanSearchVariants(roman: String): Set<String> {
        val variants = linkedSetOf(roman)
        val derived = SinglishAmbiguityVariants.liveVariants(roman).toList()
        variants.addAll(derived)
        for (spelling in derived) {
            variants.addAll(SinglishAmbiguityVariants.liveVariants(spelling))
        }
        return variants
    }

    private fun collectSinhalaPrefixes(reading: String, out: MutableSet<String>) {
        if (reading.isEmpty()) return
        out.add(reading)
        if (reading.length >= 3) out.add(reading.dropLast(1))
        if (reading.length >= 4) out.add(reading.dropLast(2))
    }

    private fun addDictionaryCorpusMatches(lower: String, corpusFrequencies: MutableMap<String, Int>) {
        dictionary.entries
            .filter { it.key.startsWith(lower) || fuzzyMatch(it.key, lower) }
            .forEach { (romanKey, sinhala) ->
                if (!containsSinhalaScript(sinhala)) return@forEach
                val weight = frequency[romanKey] ?: 1
                corpusFrequencies[sinhala] = maxOf(
                    corpusFrequencies[sinhala] ?: 0,
                    corpusDb.lookupFrequency(sinhala).takeIf { it > 0 } ?: weight,
                )
            }
    }

    private fun fuzzyMatch(dictKey: String, typed: String): Boolean {
        if (typed.length < 3 || dictKey.length < typed.length) return false
        var ti = 0
        for (c in dictKey) {
            if (ti < typed.length && c == typed[ti]) ti++
            if (ti == typed.length) return true
        }
        return false
    }

    private fun containsSinhalaScript(text: String): Boolean =
        text.any { it.code in 0x0D80..0x0DFF }
}
