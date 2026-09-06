package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Singlish IME engine: Helakuru-style phonetic conversion (layer 1–2) plus
 * dictionary predictions for lazy/alternate spellings (layer 3).
 */
class SinglishEngine(
    context: Context,
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

    fun suggestions(prefix: String, limit: Int = 12): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val personal = typingMemory?.sinhalaSuggestions(p, limit = 6).orEmpty()
        val romanVariants = romanSearchVariants(p)
        val homophoneReadings = linkedSetOf<String>()
        val sinhalaPrefixes = linkedSetOf<String>()

        for (roman in romanVariants) {
            for (reading in AlternateSinhalaReadings.forRoman(roman)) {
                homophoneReadings.add(reading)
                collectSinhalaPrefixes(reading, sinhalaPrefixes)
            }
        }

        val corpusFrequencies = LinkedHashMap<String, Int>()
        for (entry in corpusDb.queryMergedByPrefixes(sinhalaPrefixes, limitPerPrefix = 24, totalLimit = 96)) {
            corpusFrequencies[entry.word] = maxOf(corpusFrequencies[entry.word] ?: 0, entry.frequency)
        }
        for ((word, freq) in corpusDb.lookupFrequencies(homophoneReadings)) {
            corpusFrequencies[word] = maxOf(corpusFrequencies[word] ?: 0, freq)
        }
        addDictionaryCorpusMatches(lower, corpusFrequencies)

        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = p.length,
            personal = personal,
            corpusFrequencies = corpusFrequencies,
            homophoneReadings = homophoneReadings,
            limit = limit,
        )

        val results = LinkedHashSet<SuggestionCandidate>()
        for (word in ranked) {
            results.add(SuggestionCandidate(word, word))
        }

        if (results.size < limit) {
            addRomanDictionaryPrefixes(lower, results, limit)
        }

        if (results.size < limit) {
            val roman = p.replaceFirstChar { it.uppercaseChar() }
            results.add(SuggestionCandidate(roman, p, isRoman = true))
        }

        return results.take(limit).toList()
    }

    /** Instant roman-word chips for English/Singlish typing — no AI delay. */
    fun romanPrefixSuggestions(prefix: String, limit: Int = 10): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()
        val lower = p.lowercase()
        val results = LinkedHashSet<SuggestionCandidate>()
        typingMemory?.sinhalaSuggestions(p, limit = 6)?.forEach { results.add(it) }
        addRomanDictionaryPrefixes(lower, results, limit)
        return results.take(limit).toList()
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
        if (reading.length >= 3) {
            out.add(reading.dropLast(1))
        }
        if (reading.length >= 4) {
            out.add(reading.dropLast(2))
        }
    }

    /** Boost exact manual-dict targets using their local weight when corpus misses them. */
    private fun addDictionaryCorpusMatches(lower: String, corpusFrequencies: MutableMap<String, Int>) {
        dictionary.entries
            .filter { it.key.startsWith(lower) || fuzzyMatch(it.key, lower) }
            .forEach { (romanKey, sinhala) ->
                val weight = frequency[romanKey] ?: 1
                corpusFrequencies[sinhala] = maxOf(
                    corpusFrequencies[sinhala] ?: 0,
                    corpusDb.lookupFrequency(sinhala).takeIf { it > 0 } ?: weight,
                )
            }
    }

    private fun addRomanDictionaryPrefixes(
        lower: String,
        results: LinkedHashSet<SuggestionCandidate>,
        limit: Int,
    ) {
        dictionary.entries
            .filter { it.key.startsWith(lower) && it.key.length > lower.length }
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { frequency[it.key] ?: 0 }
                    .thenBy { it.key.length }
                    .thenBy { it.key },
            )
            .forEach { (romanKey, _) ->
                results.add(SuggestionCandidate(romanKey, romanKey, isRoman = true))
                if (results.size >= limit) return
            }
    }

    /** Lazy typing: "bng" matches "banga" / "bankuwa" style keys in dictionary. */
    private fun fuzzyMatch(dictKey: String, typed: String): Boolean {
        if (typed.length < 3 || dictKey.length < typed.length) return false
        var ti = 0
        for (c in dictKey) {
            if (ti < typed.length && c == typed[ti]) ti++
            if (ti == typed.length) return true
        }
        return false
    }
}
