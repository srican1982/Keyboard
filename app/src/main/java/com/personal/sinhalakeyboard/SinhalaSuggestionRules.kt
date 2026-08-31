package com.personal.sinhalakeyboard

/**
 * Keeps Sinhala suggestion chips readable — blocks runaway spellings and malformed output.
 */
object SinhalaSuggestionRules {

    private val sinhalaMatras = setOf(
        '\u0DCF', '\u0DD0', '\u0DD1', '\u0DD2', '\u0DD3', '\u0DD4', '\u0DD6',
        '\u0DD8', '\u0DD9', '\u0DDA', '\u0DDB', '\u0DDC', '\u0DDF', '\u0DE0', '\u0DF2',
    )

    private val sinhalaVowelLetters = '\u0D85'..'\u0D96'

    /** Not used for live suggestions — kept for tests / future full-word correction. */
    fun romanVariants(word: String): Set<String> {
        if (word.length < 3) return emptySet()

        val maxLen = (word.length + 2).coerceAtMost(24)
        val variants = linkedSetOf<String>()
        val replacements = listOf(
            "aa" to "a", "a" to "aa",
            "ae" to "A", "A" to "ae",
            "aee" to "AA", "AA" to "aee",
            "ee" to "e", "e" to "ee",
            "ii" to "i", "i" to "ii",
            "oo" to "o", "o" to "oo",
            "uu" to "u", "u" to "uu",
            "ch" to "c", "ph" to "f", "kh" to "k",
        )
        for ((from, to) in replacements) {
            val fromIndex = word.indexOf(from)
            if (fromIndex >= 0) {
                val next = word.replaceRange(fromIndex, fromIndex + from.length, to)
                if (next != word && next.length <= maxLen) variants.add(next)
            }
            if (from != to) {
                val toIndex = word.indexOf(to)
                if (toIndex >= 0) {
                    val next = word.replaceRange(toIndex, toIndex + to.length, from)
                    if (next != word && next.length <= maxLen) variants.add(next)
                }
            }
        }
        return variants
    }

    fun isReasonableRomanKey(roman: String): Boolean {
        val key = roman.trim().lowercase()
        if (key.isEmpty() || key.length > 32) return false
        var vowelRun = 0
        for (ch in key) {
            if (ch in "aeiou") {
                vowelRun++
                if (vowelRun > 4) return false
            } else {
                vowelRun = 0
            }
        }
        return true
    }

    fun isReasonableSinhalaSuggestion(
        sinhala: String,
        typedRomanLength: Int,
        fromCorpus: Boolean = false,
    ): Boolean {
        if (sinhala.isEmpty()) return false

        val maxLen = if (fromCorpus) 48 else (typedRomanLength * 2 + 6).coerceIn(4, 20)
        if (sinhala.length > maxLen) return false
        if (fromCorpus) return true

        var matraRun = 0
        var maxMatraRun = 0
        var matraCount = 0
        var vowelLetterRun = 0
        var lastVowelLetter: Char? = null

        for (ch in sinhala) {
            if (ch in sinhalaMatras) {
                matraCount++
                matraRun++
                maxMatraRun = maxOf(maxMatraRun, matraRun)
            } else {
                matraRun = 0
            }

            if (ch in sinhalaVowelLetters) {
                if (ch == lastVowelLetter) {
                    vowelLetterRun++
                    if (vowelLetterRun >= 2) return false
                } else {
                    vowelLetterRun = 1
                    lastVowelLetter = ch
                }
            } else if (ch !in sinhalaMatras) {
                vowelLetterRun = 0
                lastVowelLetter = null
            }
        }

        if (maxMatraRun > 1) return false
        if (matraCount > sinhala.length / 2) return false
        return true
    }

    /** Homophone / case toggles must stay close to what the user typed. */
    fun isReasonableSpellingVariant(original: String, variant: String): Boolean {
        if (variant == original) return false
        val variantKey = variant.replace(" ", "").lowercase()
        val originalKey = original.replace(" ", "").lowercase()
        if (!isReasonableRomanKey(variantKey)) return false
        if (variant.contains(" ")) {
            return variantKey.length <= originalKey.length + 4 &&
                variantKey.length + 2 >= originalKey.length
        }
        return variant.length <= original.length + 3 && variant.length + 2 >= original.length
    }
}
