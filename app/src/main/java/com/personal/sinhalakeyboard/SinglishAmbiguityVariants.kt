package com.personal.sinhalakeyboard

/**
 * Single-step Singlish spelling alternatives for the suggestion row while typing.
 * Covers vowel overload (a→අ/ආ/ඇ/ඈ), consonant+vowel stems (ka→ක/කා/කැ/කෑ),
 * dental pairs (d/dh), and pre-nasalized clusters (handa→හඳ/හන්ද/හැන්ද).
 */
object SinglishAmbiguityVariants {

    fun liveVariants(word: String): Set<String> {
        if (word.isEmpty()) return emptySet()

        val variants = linkedSetOf<String>()
        if (word.length == 1 || isVowelOnlyWord(word)) {
            variants.addAll(standaloneVowelVariants(word))
        }
        if (word.length >= 2) {
            variants.addAll(consonantVowelStemVariants(word))
            variants.addAll(vowelLengthVariants(word))
            variants.addAll(internalSyllableVowelVariants(word))
            variants.addAll(vowelAeVariants(word))
            variants.addAll(firstVowelAeVariants(word))
            variants.addAll(consonantDentalsVariants(word))
            variants.addAll(sanyakaClusterVariants(word))
            variants.addAll(anusvaraVariants(word))
            variants.addAll(anusvaraLazyNgVariants(word))
            variants.addAll(existingHomophoneVariants(word))
            val derived = variants.toList()
            for (spelling in derived) {
                variants.addAll(existingHomophoneVariants(spelling))
            }
        }
        variants.remove(word)
        return variants.filter { SinhalaSuggestionRules.isReasonableSpellingVariant(word, it) }.toSet()
    }

    /** a↔aa↔ae↔aee — standalone අ / ආ / ඇ / ඈ. */
    private fun standaloneVowelVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        when (word.lowercase()) {
            "a" -> {
                variants.add("aa")
                variants.add("ae")
                variants.add("aee")
            }
            "aa" -> {
                variants.add("a")
                variants.add("ae")
                variants.add("aee")
            }
            "ae" -> {
                variants.add("a")
                variants.add("aa")
                variants.add("aee")
            }
            "aee" -> {
                variants.add("a")
                variants.add("aa")
                variants.add("ae")
            }
            "e" -> {
                variants.add("ee")
            }
            "ee" -> {
                variants.add("e")
            }
            "i" -> {
                variants.add("ii")
            }
            "ii" -> {
                variants.add("i")
            }
            "o" -> {
                variants.add("oo")
            }
            "oo" -> {
                variants.add("o")
            }
            "u" -> {
                variants.add("uu")
            }
            "uu" -> {
                variants.add("u")
            }
        }
        return variants
    }

    /**
     * Consonant + trailing a cluster: ka→k/kaa/kae/kaee (ක / කා / කැ / කෑ).
     * Applies to the last syllable's vowel in the typed prefix.
     */
    private fun consonantVowelStemVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val lower = word.lowercase()

        when {
            lower.endsWith("aee") && lower.length > 3 -> {
                val stem = word.dropLast(3)
                if (hasConsonantStem(stem)) {
                    variants.add(stem + "a")
                    variants.add(stem + "aa")
                    variants.add(stem + "ae")
                }
            }
            lower.endsWith("ae") && !lower.endsWith("aee") && lower.length > 2 -> {
                val stem = word.dropLast(2)
                if (hasConsonantStem(stem)) {
                    variants.add(stem + "a")
                    variants.add(stem + "aa")
                    variants.add(stem + "aee")
                }
            }
            lower.endsWith("aa") && lower.length > 2 -> {
                val stem = word.dropLast(2)
                if (hasConsonantStem(stem)) {
                    variants.add(stem + "a")
                    variants.add(stem + "ae")
                    variants.add(stem + "aee")
                }
            }
            lower.endsWith("a") && !lower.endsWith("aa") && !lower.endsWith("ae") && lower.length > 1 -> {
                val stem = word.dropLast(1)
                if (hasConsonantStem(stem)) {
                    variants.add(stem)
                    variants.add(stem + "aa")
                    variants.add(stem + "ae")
                    variants.add(stem + "aee")
                }
            }
        }
        return variants
    }

    private fun hasConsonantStem(stem: String): Boolean {
        if (stem.isEmpty()) return false
        val last = stem.last().lowercaseChar()
        return last !in "aeiou"
    }

    /** First syllable æ: handa→haenda (හැන්ද-style). */
    private fun firstVowelAeVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val match = Regex("(?<![aeiou])a(?![aeiou])").find(word) ?: return variants
        variants.add(word.replaceRange(match.range, "ae"))
        val aeMatch = Regex("(?<![aeiou])ae(?![aeiou])").find(word)
        if (aeMatch != null) {
            variants.add(word.replaceRange(aeMatch.range, "a"))
        }
        return variants
    }

    /**
     * Pre-nasalized (ඳ/ඬ) vs split න+ද, and æ (ැ):
     *   handa → හඳ (nda→ඳ), hanDa → හඬ (nDa→ඬ), haendha → හැන්ද (ndha + ae)
     */
    private fun sanyakaClusterVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val lower = word.lowercase()

        if (lower.contains("nda")) {
            // ඳ (sanyaka dha) is the default — keep typed form as-is.
            // ඬ (sanyaka da): nda → nDa — e.g. handa → hanDa → හඬ
            variants.add(word.replaceFirst("nda", "nDa", ignoreCase = true))
            // න+ද (not pre-nasalized): nda → ndha — e.g. haendha → හැන්ද
            val ndhaForm = word.replaceFirst("nda", "ndha", ignoreCase = true)
            variants.add(ndhaForm)
            for (aeForm in firstVowelAeVariants(ndhaForm)) {
                variants.add(aeForm)
            }
            variants.add(word.replaceFirst("nda", "n dha", ignoreCase = true))
            val ndaIndex = lower.indexOf("nda")
            if (ndaIndex > 0 && lower[ndaIndex - 1] == 'a') {
                val withAe = word.substring(0, ndaIndex - 1) + "ae" +
                    word.substring(ndaIndex).replaceFirst("nda", "n dha", ignoreCase = true)
                variants.add(withAe)
            }
        }
        if (lower.contains("nd") && !lower.contains("nda") && !lower.contains("n dha")) {
            variants.add(word.replaceFirst("nd", "n dh", ignoreCase = true))
        }
        if (lower.contains("mb") && lower.length > 2) {
            variants.add(word.replaceFirst("mb", "m b", ignoreCase = true))
        }
        return variants
    }

    private fun isVowelOnlyWord(word: String): Boolean =
        word.all { it.lowercaseChar() in "aeiou" }

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

    /**
     * Mid-word pillam: ko→koo, na→naa (e.g. konara → කෝනර / කෝනාර / කෝණාර).
     */
    private fun internalSyllableVowelVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val lower = word.lowercase()
        val vowels = "aeiou"

        val lengthenedOoEe = linkedSetOf<String>()
        for (i in word.indices) {
            if (lower[i] == 'o' && !lower.regionMatches(i, "oo", 0, 2)) {
                val next = lower.getOrNull(i + 1)
                if (next != null && next !in vowels) {
                    lengthenedOoEe.add(word.substring(0, i) + "oo" + word.substring(i + 1))
                }
            }
            if (lower[i] == 'e' && !lower.regionMatches(i, "ee", 0, 2)) {
                val next = lower.getOrNull(i + 1)
                if (next != null && next !in vowels) {
                    lengthenedOoEe.add(word.substring(0, i) + "ee" + word.substring(i + 1))
                }
            }
        }
        variants.addAll(lengthenedOoEe)
        variants.addAll(lengthenConsonantSandwichVowels(word))
        for (form in lengthenedOoEe) {
            variants.addAll(lengthenConsonantSandwichVowels(form))
        }

        return variants
    }

    /** a/i/u between consonants → aa/ii/uu (e.g. koonara → koonaara). */
    private fun lengthenConsonantSandwichVowels(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val lower = word.lowercase()
        val vowels = "aeiou"
        for (i in 1 until word.length - 1) {
            val prev = lower[i - 1]
            val ch = lower[i]
            val next = lower[i + 1]
            if (prev !in vowels && next !in vowels) {
                when {
                    ch == 'a' && !lower.regionMatches(i, "aa", 0, 2) ->
                        variants.add(word.substring(0, i) + "aa" + word.substring(i + 1))
                    ch == 'i' && !lower.regionMatches(i, "ii", 0, 2) ->
                        variants.add(word.substring(0, i) + "ii" + word.substring(i + 1))
                    ch == 'u' && !lower.regionMatches(i, "uu", 0, 2) ->
                        variants.add(word.substring(0, i) + "uu" + word.substring(i + 1))
                }
            }
        }
        return variants
    }

    /** aa↔ae↔aee anywhere in the word. */
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

    /** Insert g after n before velars — sank↔sangk (bindu spelling). */
    private fun anusvaraLazyNgVariants(word: String): Set<String> {
        val variants = linkedSetOf<String>()
        val lower = word.lowercase()
        val triggers = listOf("k", "g", "c", "j", "t", "p", "b", "m", "s", "h")
        var i = 0
        while (i < word.length - 1) {
            if (word[i].equals('n', ignoreCase = true)) {
                val tail = lower.substring(i + 1)
                if (!tail.startsWith("d") && !tail.startsWith("g") &&
                    triggers.any { tail.startsWith(it) }
                ) {
                    variants.add(word.substring(0, i + 1) + "g" + word.substring(i + 1))
                }
            }
            i++
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
        val index = word.indexOf(from, ignoreCase = false)
        if (index >= 0) {
            out.add(word.replaceRange(index, index + from.length, to))
        }
    }
}
