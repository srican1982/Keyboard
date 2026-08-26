package com.personal.sinhalakeyboard

/**
 * Single-step Singlish spelling alternatives for the suggestion row while typing.
 * Each variant changes at most one ambiguous cluster (d/dh, o/oo, n/ng, aa/ae, …).
 */
object SinglishAmbiguityVariants {

    fun liveVariants(word: String): Set<String> {
        if (word.length < 2) return emptySet()

        val variants = linkedSetOf<String>()
        variants.addAll(vowelLengthVariants(word))
        variants.addAll(vowelAeVariants(word))
        variants.addAll(consonantDentalsVariants(word))
        variants.addAll(anusvaraVariants(word))
        variants.addAll(existingHomophoneVariants(word))
        variants.remove(word)
        return variants.filter { SinhalaSuggestionRules.isReasonableSpellingVariant(word, it) }.toSet()
    }

    /** o↔oo, e↔ee, i↔ii, u↔uu — e.g. ko→koo (කො/කෝ), kee→ke (කී/කෙ). */
    private fun vowelLengthVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        toggleSuffix(word, "oo", "o", variants)
        toggleSuffix(word, "o", "oo", variants) { !word.endsWith("oo") }
        toggleSuffix(word, "ee", "e", variants)
        toggleSuffix(word, "e", "ee", variants) { !word.endsWith("ee") && !word.endsWith("ae") }
        toggleSuffix(word, "ii", "i", variants)
        toggleSuffix(word, "i", "ii", variants) { !word.endsWith("ii") }
        toggleSuffix(word, "uu", "u", variants)
        toggleSuffix(word, "u", "uu", variants) { !word.endsWith("uu") }
        return variants
    }

    /** aa↔ae↔aee — e.g. kaa→kae/kaee (කා/කැ/කෑ). */
    private fun vowelAeVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        replaceFirst(word, "aee", "ae", variants)
        replaceFirst(word, "aee", "aa", variants)
        replaceFirst(word, "AA", "aee", variants)
        replaceFirst(word, "AA", "ae", variants)
        replaceFirst(word, "ae", "aa", variants)
        replaceFirst(word, "ae", "aee", variants)
        replaceFirst(word, "A", "ae", variants) { word.endsWith("A") && !word.endsWith("AA") }
        replaceFirst(word, "aa", "ae", variants)
        replaceFirst(word, "aa", "aee", variants)
        return variants
    }

    /** d↔dh, t↔th — e.g. da→dha (ඩ/ද), ta→tha (ට/ත). */
    private fun consonantDentalsVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        var i = 0
        while (i < word.length) {
            when {
                word.regionMatches(i, "dh", 0, 2, ignoreCase = true) -> {
                    variants.add(word.substring(0, i) + "d" + word.substring(i + 2))
                    i += 2
                }
                word.regionMatches(i, "th", 0, 2, ignoreCase = true) -> {
                    variants.add(word.substring(0, i) + "t" + word.substring(i + 2))
                    i += 2
                }
                word[i] == 'd' && (i + 1 >= word.length || word[i + 1] != 'h') &&
                    (i == 0 || word[i - 1] != 'n') -> {
                    variants.add(word.substring(0, i) + "dh" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 't' && (i + 1 >= word.length || word[i + 1] != 'h') -> {
                    variants.add(word.substring(0, i) + "th" + word.substring(i + 1))
                    i += 1
                }
                else -> i += 1
            }
        }
        return variants
    }

    /** Word-final n↔ng — e.g. tan→tang (ටන්/ටං). */
    private fun anusvaraVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        if (word.endsWith("ng") && word.length > 2) {
            variants.add(word.dropLast(2) + "n")
        }
        if (word.endsWith('n') && !word.endsWith("ng")) {
            variants.add(word.dropLast(1) + "ng")
        }
        return variants
    }

    /** Case / mahaprana toggles (sh/Sh, n/N, kh/K, …). */
    private fun existingHomophoneVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val digraphPairs = listOf(
            "kh" to "K", "K" to "kh",
            "gh" to "G", "G" to "gh",
            "ph" to "P", "P" to "ph",
            "bh" to "B", "B" to "bh",
            "ch" to "Ch", "Ch" to "ch",
            "Sh" to "sh", "sh" to "Sh",
            "th" to "T", "T" to "th",
        )
        for ((from, to) in digraphPairs) {
            replaceFirst(word, from, to, variants)
        }

        var i = 0
        while (i < word.length) {
            when {
                word.regionMatches(i, "sh", 0, 2, ignoreCase = true) -> i += 2
                word[i] == 'n' && (i + 1 >= word.length || word[i + 1] != 'g') -> {
                    variants.add(word.substring(0, i) + "N" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'N' -> {
                    variants.add(word.substring(0, i) + "n" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'l' -> {
                    variants.add(word.substring(0, i) + "L" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'L' -> {
                    variants.add(word.substring(0, i) + "l" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'j' && (i + 1 >= word.length || word[i + 1] != 'h') -> {
                    variants.add(word.substring(0, i) + "J" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'J' && (i + 1 >= word.length || word[i + 1] != 'h') -> {
                    variants.add(word.substring(0, i) + "j" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 's' && (i + 1 >= word.length || !word.regionMatches(i + 1, "h", 0, 1, true)) -> {
                    variants.add(word.substring(0, i) + "S" + word.substring(i + 1))
                    i += 1
                }
                word[i] == 'S' && (i + 1 >= word.length || !word.regionMatches(i + 1, "h", 0, 1, true)) -> {
                    variants.add(word.substring(0, i) + "s" + word.substring(i + 1))
                    i += 1
                }
                else -> i += 1
            }
        }
        return variants
    }

    private inline fun toggleSuffix(
        word: String,
        from: String,
        to: String,
        out: MutableSet<String>,
        extra: () -> Boolean = { true },
    ) {
        if (extra() && word.endsWith(from)) {
            out.add(word.dropLast(from.length) + to)
        }
    }

    private inline fun replaceFirst(
        word: String,
        from: String,
        to: String,
        out: MutableSet<String>,
        extra: () -> Boolean = { true },
    ) {
        if (!extra()) return
        val index = word.indexOf(from)
        if (index >= 0) {
            out.add(word.replaceRange(index, index + from.length, to))
        }
    }
}
