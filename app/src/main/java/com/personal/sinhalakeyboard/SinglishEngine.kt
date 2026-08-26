package com.personal.sinhalakeyboard

import android.content.Context

/**
 * Singlish IME engine: Helakuru-style phonetic conversion (layer 1–2) plus
 * dictionary predictions for lazy/alternate spellings (layer 3).
 */
class SinglishEngine(context: Context) {

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

    fun suggestions(prefix: String, limit: Int = 8): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val results = LinkedHashSet<SuggestionCandidate>()

        // Layer 3: dictionary prefix + lazy spellings (higher frequency first)
        dictionary.entries
            .filter { it.key.startsWith(lower) || fuzzyMatch(it.key, lower) }
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { frequency[it.key] ?: 0 }
                    .thenBy { it.key.length }
                    .thenBy { it.key },
            )
            .forEach { (key, sinhala) ->
                results.add(SuggestionCandidate(sinhala, sinhala))
                if (results.size >= limit) return results.take(limit).toList()
            }

        // Rule-based output of what was typed + spelling variants
        val ruleOutput = SinglishConverter.convert(p)
        if (ruleOutput.isNotEmpty()) {
            results.add(SuggestionCandidate(ruleOutput, ruleOutput))
        }
        for (variant in romanVariants(lower)) {
            val sinhala = SinglishConverter.convert(variant)
            if (sinhala.isNotEmpty()) results.add(SuggestionCandidate(sinhala, sinhala))
        }

        // Roman / Singlish keep-as-typed option
        val roman = p.replaceFirstChar { it.uppercaseChar() }
        results.add(SuggestionCandidate(roman, p, isRoman = true))

        return results.take(limit).toList()
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

    private fun romanVariants(word: String): Set<String> {
        val variants = linkedSetOf(word)
        val replacements = listOf(
            "aa" to "a", "a" to "aa",
            "ae" to "A", "A" to "ae",
            "aee" to "AA", "AA" to "aee",
            "ee" to "e", "e" to "ee",
            "ii" to "i", "i" to "ii",
            "oo" to "o", "o" to "oo",
            "uu" to "u", "u" to "uu",
            "th" to "t", "t" to "th",
            "dh" to "d", "d" to "dh",
            "sh" to "s", "ch" to "c",
            "ph" to "f", "kh" to "k",
        )
        val queue = ArrayDeque<String>()
        queue.add(word)
        while (queue.isNotEmpty() && variants.size < 16) {
            val current = queue.removeFirst()
            for ((from, to) in replacements) {
                if (current.contains(from)) {
                    val next = current.replace(from, to, ignoreCase = false)
                    if (next !in variants) {
                        variants.add(next)
                        queue.add(next)
                    }
                }
            }
        }
        return variants
    }
}
