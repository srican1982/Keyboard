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

    fun suggestions(prefix: String, limit: Int = 8): List<SuggestionCandidate> {
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()

        val lower = p.lowercase()
        val results = LinkedHashSet<SuggestionCandidate>()

        // Layer 0: words you typed before (highest priority)
        typingMemory?.sinhalaSuggestions(p, limit = 4)?.forEach { results.add(it) }

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

        // Rule-based output + ambiguous consonant variants (sh/ශ vs Sh/ෂ, etc.)
        val ruleOutput = SinglishConverter.convert(p)
        if (ruleOutput.isNotEmpty()) {
            results.add(SuggestionCandidate(ruleOutput, ruleOutput))
        }
        for (variant in consonantAmbiguityVariants(p) + homophoneAmbiguityVariants(p) + vowelAmbiguityVariants(p)) {
            val sinhala = SinglishConverter.convert(variant)
            if (sinhala.isNotEmpty() && sinhala != ruleOutput) {
                results.add(SuggestionCandidate(sinhala, sinhala))
            }
        }
        for (variant in romanVariants(lower)) {
            val sinhala = SinglishConverter.convert(variant)
            if (sinhala.isNotEmpty() && sinhala != ruleOutput) {
                results.add(SuggestionCandidate(sinhala, sinhala))
            }
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

    /** sh→Sh (ශ vs ෂ), s→S, and similar Singlish ambiguities. */
    private fun consonantAmbiguityVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        var i = 0
        while (i < word.length) {
            when {
                word.regionMatches(i, "sh", 0, 2, ignoreCase = true) -> {
                    variants.add(word.substring(0, i) + "Sh" + word.substring(i + 2))
                    variants.add(word.substring(0, i) + "sh" + word.substring(i + 2))
                    i += 2
                }
                word[i] == 's' && (i + 1 >= word.length || !word.regionMatches(i + 1, "h", 0, 1, true)) -> {
                    variants.add(word.substring(0, i) + "S" + word.substring(i + 1))
                    variants.add(word.substring(0, i) + "s" + word.substring(i + 1))
                    i += 1
                }
                else -> i += 1
            }
        }
        variants.remove(word)
        return variants
    }

    /** n/N, l/L, k/K, sh/Sh/s/S, ch/Ch, j/J and other case-pair toggles for suggestions. */
    private fun homophoneAmbiguityVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val pairs = listOf(
            "th" to "T", "T" to "th",
            "n" to "N", "N" to "n",
            "l" to "L", "L" to "l",
            "kh" to "K", "K" to "kh",
            "gh" to "G", "G" to "gh",
            "ph" to "P", "P" to "ph",
            "bh" to "B", "B" to "bh",
            "ch" to "Ch", "Ch" to "ch",
            "Sh" to "sh", "sh" to "Sh",
        )
        for ((from, to) in pairs) {
            if (word.contains(from)) {
                variants.add(word.replace(from, to))
            }
        }
        var i = 0
        while (i < word.length) {
            if (word[i] == 'j' && (i + 1 >= word.length || word[i + 1] != 'h')) {
                variants.add(word.substring(0, i) + "J" + word.substring(i + 1))
                i += 1
            } else if (word[i] == 'J' && (i + 1 >= word.length || word[i + 1] != 'h')) {
                variants.add(word.substring(0, i) + "j" + word.substring(i + 1))
                i += 1
            } else if (word[i] == 's' && (i + 1 >= word.length || !word.regionMatches(i + 1, "h", 0, 1, true))) {
                variants.add(word.substring(0, i) + "S" + word.substring(i + 1))
                i += 1
            } else if (word[i] == 'S' && (i + 1 >= word.length || !word.regionMatches(i + 1, "h", 0, 1, true))) {
                variants.add(word.substring(0, i) + "s" + word.substring(i + 1))
                i += 1
            } else {
                i += 1
            }
        }
        variants.remove(word)
        return variants
    }

    /** she→shee (ශෙ vs ශේ) and other short/long vowel toggles after sh/s clusters. */
    private fun vowelAmbiguityVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        if (word.endsWith("e") && !word.endsWith("ee") && word.length > 1) {
            variants.add(word.dropLast(1) + "ee")
        }
        if (word.endsWith("ee") && word.length > 2) {
            variants.add(word.dropLast(2) + "e")
        }
        if (word.endsWith("ae") && word.length > 2) {
            variants.add(word.dropLast(2) + "A")
            variants.add(word.dropLast(2) + "aee")
        }
        if (word.endsWith("A") && word.length > 1) {
            variants.add(word.dropLast(1) + "ae")
        }
        if (word.endsWith("aee") && word.length > 3) {
            variants.add(word.dropLast(3) + "AA")
            variants.add(word.dropLast(3) + "ae")
        }
        if (word.endsWith("AA") && word.length > 2) {
            variants.add(word.dropLast(2) + "aee")
        }
        variants.remove(word)
        return variants
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
            "ch" to "c",
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
