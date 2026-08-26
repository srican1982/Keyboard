package com.personal.sinhalakeyboard

import android.content.Context

class SinglishEngine(context: Context) {

    private val dictionary: MutableMap<String, String> = mutableMapOf()
    private val sortedKeys: MutableList<String> = mutableListOf()

    init {
        loadDictionary(context)
    }

    private fun loadDictionary(context: Context) {
        try {
            context.assets.open("sinhala_dict.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split("|", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].lowercase()
                        val value = parts[1]
                        if (key.isNotBlank() && value.isNotBlank()) {
                            dictionary[key] = value
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fall back to rules only
        }
        sortedKeys.clear()
        sortedKeys.addAll(dictionary.keys.sorted())
    }

    fun transliterate(input: String): String {
        val word = input.lowercase().trim()
        if (word.isEmpty()) return ""
        dictionary[word]?.let { return it }
        return transliterateWord(word)
    }

    fun transliterateLive(input: String): String = transliterateWord(input.lowercase().trim())

    /** Multiple Sinhala + roman suggestions for ANY typed word. */
    fun suggestions(prefix: String, limit: Int = 8): List<SuggestionCandidate> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty()) return emptyList()

        val results = LinkedHashSet<SuggestionCandidate>()

        // Dictionary prefix matches (enn→enna, ennam…)
        dictionary.entries
            .filter { it.key.startsWith(p) }
            .sortedWith(compareBy<Map.Entry<String, String>> { it.key.length }.thenBy { it.key })
            .forEach { (_, sinhala) -> results.add(SuggestionCandidate(sinhala, sinhala)) }

        // Rule-based transliteration of typed text + spelling variants
        for (variant in romanVariants(p)) {
            val sinhala = transliterateWord(variant)
            if (sinhala.isNotEmpty()) {
                results.add(SuggestionCandidate(sinhala, sinhala))
            }
            dictionary[variant]?.let { results.add(SuggestionCandidate(it, it)) }
        }

        // Extended word forms (enn→ennam, enna, ennavva…)
        for (suffix in COMMON_SUFFIXES) {
            val extended = p + suffix
            dictionary[extended]?.let { results.add(SuggestionCandidate(it, it)) }
            val t = transliterateWord(extended)
            if (t.isNotEmpty()) results.add(SuggestionCandidate(t, t))
        }

        // Shorter completions (enna→en, en)
        for (len in p.length - 1 downTo 1) {
            val sub = p.substring(0, len)
            dictionary[sub]?.let { results.add(SuggestionCandidate(it, it)) }
            val t = transliterateWord(sub)
            if (t.isNotEmpty()) results.add(SuggestionCandidate(t, t))
        }

        // Roman / Singlish option (like Desh shows "Enna")
        val roman = p.replaceFirstChar { it.uppercaseChar() }
        results.add(SuggestionCandidate(roman, p, isRoman = true))

        return results.take(limit).toList()
    }

    /** Generate spelling variants: aa/a, ee/e, th/t, etc. */
    private fun romanVariants(word: String): Set<String> {
        val variants = linkedSetOf(word)
        val replacements = listOf(
            "aa" to "a", "a" to "aa",
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
        while (queue.isNotEmpty() && variants.size < 20) {
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

    companion object {
        private const val HAL = "\u0DCA"

        private val COMMON_SUFFIXES = listOf(
            "m", "ma", "da", "ta", "na", "wa", "waa", "yi", "y", "e", "i", "o",
            "la", "rai", "ne", "ge", "k", "nn", "ava", "nava", "e", "ei",
        )

        private val consonantMap = mapOf(
            "dh" to "\u0DAF", "th" to "\u0DAD", "ch" to "\u0DA0", "sh" to "\u0DC1",
            "ng" to "\u0D9E", "ny" to "\u0DA4", "kh" to "\u0D9B", "gh" to "\u0D9D",
            "ph" to "\u0DB5", "bh" to "\u0DB7", "k" to "\u0D9A", "g" to "\u0D9C",
            "j" to "\u0DA2", "t" to "\u0DA7", "d" to "\u0DA9", "n" to "\u0DB1",
            "p" to "\u0DB4", "b" to "\u0DB6", "m" to "\u0DB8", "y" to "\u0DBA",
            "r" to "\u0DBB", "l" to "\u0DBD", "v" to "\u0DC0", "w" to "\u0DC0",
            "s" to "\u0DC3", "h" to "\u0DC4", "f" to "\u0DC6", "L" to "\u0DC5",
        )

        private val consonantPatterns = consonantMap.keys.sortedByDescending { it.length }

        private val vowelSigns = linkedMapOf(
            "aa" to "\u0DCF", "ii" to "\u0DD3", "uu" to "\u0DD6", "ee" to "\u0DDA",
            "ai" to "\u0DDB", "oo" to "\u0DDF", "au" to "\u0DE0", "a" to "\u0DCF",
            "i" to "\u0DD2", "u" to "\u0DD4", "e" to "\u0DD9", "o" to "\u0DDC",
        )

        private val standaloneVowels = linkedMapOf(
            "aa" to "\u0D86", "ii" to "\u0D88", "uu" to "\u0D8A", "ee" to "\u0DDA",
            "ai" to "\u0D8E", "oo" to "\u0D94", "au" to "\u0D96", "a" to "\u0D85",
            "i" to "\u0D89", "u" to "\u0D8B", "e" to "\u0D91", "o" to "\u0D94",
        )

        private fun transliterateWord(input: String): String {
            if (input.isEmpty()) return ""
            val result = StringBuilder()
            var i = 0
            while (i < input.length) {
                val (consonant, cLen) = matchConsonant(input, i)
                if (consonant != null) {
                    i += cLen
                    val (vowel, vLen) = matchVowel(input, i)
                    if (vowel != null) {
                        i += vLen
                        result.append(consonant).append(vowelSigns[vowel].orEmpty())
                    } else {
                        result.append(consonant).append(HAL)
                    }
                } else {
                    val (vowel, vLen) = matchVowel(input, i)
                    if (vowel != null) {
                        i += vLen
                        result.append(standaloneVowels[vowel].orEmpty())
                    } else {
                        result.append(input[i])
                        i++
                    }
                }
            }
            return result.toString()
        }

        private fun matchConsonant(input: String, start: Int): Pair<String?, Int> {
            val slice = input.substring(start)
            for (pattern in consonantPatterns) {
                if (slice.startsWith(pattern)) {
                    return consonantMap[pattern] to pattern.length
                }
            }
            return null to 0
        }

        private fun matchVowel(input: String, start: Int): Pair<String?, Int> {
            if (start >= input.length) return null to 0
            val slice = input.substring(start)
            for (key in vowelSigns.keys) {
                if (slice.startsWith(key)) return key to key.length
            }
            return null to 0
        }
    }
}
