package com.personal.sinhalakeyboard

import java.text.Normalizer
import java.util.Locale

/** Shared pronunciation signatures, not a list of special-case words. */
object SinhalaPronunciation {
    private val repeatedVowels = Regex("([aeiou])\\1+")
    private val nasalCluster = Regex("ng(?=[kg])")
    private val consonants = mapOf(
        'ක' to "k", 'ඛ' to "k", 'ග' to "g", 'ඝ' to "g", 'ඞ' to "n", 'ඟ' to "ng",
        'ච' to "ch", 'ඡ' to "ch", 'ජ' to "j", 'ඣ' to "j", 'ඤ' to "ny", 'ඥ' to "ny", 'ඦ' to "nj",
        'ට' to "t", 'ඨ' to "t", 'ත' to "t", 'ථ' to "t", 'ඩ' to "d", 'ඪ' to "d",
        'ද' to "d", 'ධ' to "d", 'ඬ' to "nd", 'ඳ' to "nd", 'ණ' to "n", 'න' to "n",
        'ප' to "p", 'ඵ' to "p", 'බ' to "b", 'භ' to "b", 'ම' to "m", 'ඹ' to "mb",
        'ය' to "y", 'ර' to "r", 'ල' to "l", 'ළ' to "l", 'ව' to "v", 'ස' to "s",
        'ශ' to "sh", 'ෂ' to "sh", 'හ' to "h", 'ෆ' to "f",
    )
    private val vowels = mapOf(
        'අ' to "a", 'ආ' to "a", 'ඇ' to "a", 'ඈ' to "a", 'ඉ' to "i", 'ඊ' to "i",
        'උ' to "u", 'ඌ' to "u", 'එ' to "e", 'ඒ' to "e", 'ඔ' to "o", 'ඕ' to "o",
        'ඓ' to "ai", 'ඖ' to "au", 'ඍ' to "ru", 'ඎ' to "ru", 'ඏ' to "lu", 'ඐ' to "lu",
    )
    private val signs = mapOf(
        'ා' to "a", 'ැ' to "a", 'ෑ' to "a", 'ි' to "i", 'ී' to "i", 'ු' to "u", 'ූ' to "u",
        'ෙ' to "e", 'ේ' to "e", 'ෛ' to "ai", 'ො' to "o", 'ෝ' to "o", 'ෞ' to "au",
        'ෘ' to "ru", 'ෲ' to "ru", 'ෟ' to "lu", 'ෳ' to "lu", '්' to "",
    )

    fun sinhala(word: String): String {
        val text = Normalizer.normalize(word, Normalizer.Form.NFC).replace("\u200D", "").replace("\u200C", "")
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            val consonant = consonants[c]
            when {
                consonant != null -> {
                    out.append(consonant)
                    if (i < text.length && signs.containsKey(text[i])) out.append(signs[text[i++]])
                    else out.append('a')
                }
                vowels.containsKey(c) -> out.append(vowels[c])
                c == 'ං' -> out.append('n')
                c == 'ඃ' -> out.append('h')
                else -> return "" // Exclude punctuation, mixed-script and malformed corpus entries.
            }
        }
        return fold(out.toString())
    }

    fun roman(text: String): String {
        val raw = text.trim().lowercase(Locale.ROOT)
        if (raw.isEmpty() || raw.any { it !in 'a'..'z' }) return ""
        return fold(raw.replace("aee", "a").replace("aae", "a").replace("ae", "a")
            .replace("th", "t").replace("dh", "d").replace("kh", "k")
            .replace("gh", "g").replace("bh", "b").replace("ph", "p")
            .replace('w', 'v').replace('x', 'n'))
    }

    private fun fold(value: String): String = value
        .replace(repeatedVowels, "$1")
        // Anusvara, a nasal cluster and a pre-nasalized consonant share a lookup route.
        .replace(nasalCluster, "n")
}

/** Built lazily on the suggestion worker. No dictionary scan on each keystroke. */
class SinhalaPhoneticIndex(entries: List<Entry>) {
    data class Entry(val word: String, val frequency: Int)
    data class Match(val word: String, val frequency: Int, val complete: Boolean)
    private data class Indexed(val key: String, val entry: Entry)
    private val sorted = entries.mapNotNull { entry ->
        SinhalaPronunciation.sinhala(entry.word).takeIf { it.isNotEmpty() }?.let { Indexed(it, entry) }
    }.sortedBy { it.key }
    private val cache = object : LinkedHashMap<String, List<Match>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Match>>?) = size > 64
    }

    @Synchronized
    fun query(roman: String): List<Match> {
        val key = SinhalaPronunciation.roman(roman)
        if (key.isEmpty()) return emptyList()
        return cache.getOrPut(key) {
            val start = lowerBound(key)
            val end = lowerBound(key + '{')
            // Keep all complete matches so personal history can rank even rare words.
            val exact = ArrayList<Match>()
            val completions = java.util.PriorityQueue<Match>(compareBy { it.frequency })
            for (i in start until end) {
                val row = sorted[i]
                val match = Match(row.entry.word, row.entry.frequency, row.key == key)
                if (match.complete) exact.add(match)
                else {
                    completions.add(match)
                    if (completions.size > 48) completions.poll()
                }
            }
            exact + completions.sortedByDescending { it.frequency }
        }
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high).ushr(1)
            if (sorted[mid].key < key) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        fun rank(matches: List<Match>, counts: Map<String, Int>, limit: Int): List<String> = matches
            .sortedWith(compareByDescending<Match> { it.complete }
                .thenByDescending { counts[it.word] ?: 0 }
                .thenByDescending { it.frequency }
                .thenBy { it.word })
            .map { it.word }.distinct().take(limit)
    }
}
