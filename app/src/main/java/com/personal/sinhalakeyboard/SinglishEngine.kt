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

    init {
        loadDictionary(context)
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
        val results = LinkedHashSet<SuggestionCandidate>()

        fun addSinhala(sinhala: String) {
            if (!SinhalaSuggestionRules.isReasonableSinhalaSuggestion(sinhala, p.length)) return
            results.add(SuggestionCandidate(sinhala, sinhala))
        }

        // Layer 0: words you typed before (personal history)
        typingMemory?.sinhalaSuggestions(p, limit = 5)?.forEach { results.add(it) }

        // Layer 0.5: longer roman dictionary words (e.g. ko → koheda, kohomada)
        addRomanDictionaryPrefixes(lower, results, limit)

        // Layer 1: live conversion + phonetic ambiguities (කො/කෝ for ko) — always before dictionary
        val ruleOutput = SinglishConverter.convert(p)
        if (ruleOutput.isNotEmpty()) {
            addSinhala(ruleOutput)
        }
        for (variant in SinglishAmbiguityVariants.liveVariants(p)) {
            val sinhala = SinglishConverter.convert(variant)
            if (sinhala.isNotEmpty() && sinhala != ruleOutput) {
                addSinhala(sinhala)
            }
        }

        // Layer 2: dictionary prefix + lazy spellings
        dictionary.entries
            .filter { it.key.startsWith(lower) || fuzzyMatch(it.key, lower) }
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { frequency[it.key] ?: 0 }
                    .thenBy { it.key.length }
                    .thenBy { it.key },
            )
            .forEach { (_, sinhala) ->
                addSinhala(sinhala)
                if (results.size >= limit) return results.take(limit).toList()
            }

        // Layer 3: keep-as-Singlish (capitalized preview)
        val roman = p.replaceFirstChar { it.uppercaseChar() }
        results.add(SuggestionCandidate(roman, p, isRoman = true))

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
