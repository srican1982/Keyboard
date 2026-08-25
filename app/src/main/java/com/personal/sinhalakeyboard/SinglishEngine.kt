package com.personal.sinhalakeyboard

/**
 * Converts Singlish (Roman) input to Sinhala Unicode.
 * Example: thaththa → තාත්තා, mama → මම
 */
object SinglishEngine {

    private const val HAL = "\u0DCA" // ්

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

    val dictionary: Map<String, String> = mapOf(
        "mama" to "\u0DB8\u0DB8",
        "amma" to "\u0D85\u0DB8\u0DCA\u0DB8",
        "thaththa" to "\u0DAD\u0DCF\u0DAD\u0DCA\u0DAD\u0DCF",
        "nangi" to "\u0DBA\u0D82\u0DA2\u0DD2",
        "ayubowan" to "\u0D86\u0DBA\u0DD4\u0DB6\u0DDC\u0DC0\u0DB1\u0DCA",
        "kohomada" to "\u0D9A\u0DDC\u0DC4\u0DDC\u0DB8\u0DAF",
        "kohomath" to "\u0D9A\u0DDC\u0DC4\u0DDC\u0DB8\u0DAF",
        "stuti" to "\u0DC3\u0DCA\u0DAD\u0DD2\u0DAD\u0DD2",
        "istuti" to "\u0D87\u0DC3\u0DCA\u0DAD\u0DD2\u0DAD\u0DD2",
        "oyata" to "\u0D94\u0DBA\u0DCF\u0DA7",
        "mata" to "\u0DB8\u0DA7",
        "hari" to "\u0DC4\u0DBA\u0DD2",
        "ow" to "\u0D94\u0DC0\u0DCA",
        "na" to "\u0DAB",
        "enna" to "\u0D9A\u0DB1\u0DBA\u0DB1",
        "yanna" to "\u0DB8\u0DB1\u0DBA\u0DB1",
        "samawenna" to "\u0DC3\u0DB8\u0DC0\u0DB1\u0DBA\u0DB1",
    )

    fun transliterate(input: String): String {
        val word = input.lowercase().trim()
        if (word.isEmpty()) return ""
        dictionary[word]?.let { return it }
        return transliterateWord(word)
    }

    /** Returns Sinhala suggestions for the current Singlish prefix. */
    fun suggestions(prefix: String, limit: Int = 5): List<String> {
        val p = prefix.lowercase().trim()
        if (p.length < 2) return emptyList()

        val matches = dictionary.entries
            .filter { it.key.startsWith(p) }
            .sortedBy { it.key.length }
            .map { it.value }
            .distinct()

        if (matches.isNotEmpty()) return matches.take(limit)

        return listOf(transliterate(p))
    }

    private fun transliterateWord(input: String): String {
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
