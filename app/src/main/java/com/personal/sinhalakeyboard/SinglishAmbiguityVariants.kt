package com.personal.sinhalakeyboard

/**
 * High-value phonetic alternatives for live Singlish typing.
 *
 * DESIGN GOAL
 * -----------
 *
 * This is NOT a dictionary and does NOT contain word-specific corrections.
 *
 * Instead it generates a small, prioritized set of Roman alternatives
 * representing common Sinhala phonetic ambiguity.
 *
 * Examples:
 *
 * da
 *   d  -> ද
 *   D  -> ඩ
 *
 * than
 *   than  -> තන්
 *   thaen -> තැන්
 *   thaN  -> තණ...
 *   thax  -> තං...
 *
 * sankayaawa
 *   n before k can also represent anusvara:
 *   sank... -> saxk...
 *
 * "x" is intentionally used for anusvara because SinglishConverter
 * already maps:
 *
 *     x -> ං
 *
 * IMPORTANT:
 * liveVariants() is ordered.
 *
 * SinglishEngine only consumes a limited number of variants for
 * performance, so HIGH-VALUE phonetic alternatives MUST be inserted
 * before broad/weak spelling alternatives.
 */
object SinglishAmbiguityVariants {

    /**
     * Generate one-step, prioritized alternatives.
     *
     * LinkedHashSet preserves insertion order.
     */
    fun liveVariants(word: String): Set<String> {

        val typed = word.trim()

        if (typed.isEmpty()) {
            return emptySet()
        }

        val variants = linkedSetOf<String>()

        /*
         * ============================================================
         * PRIORITY 1
         * Sinhala consonant / nasal ambiguity
         * ============================================================
         *
         * These are the most important because they can completely
         * change which Sinhala dictionary branch is searched.
         */

        variants.addAll(
            highPriorityConsonantVariants(typed)
        )

        variants.addAll(
            highPriorityNasalVariants(typed)
        )

        /*
         * ============================================================
         * PRIORITY 2
         * a / ae ambiguity
         * ============================================================
         *
         * Very common in informal Singlish:
         *
         * pati...  <-> paeti...
         * than     <-> thaen
         */

        variants.addAll(
            highPriorityAeVariants(typed)
        )

        /*
         * ============================================================
         * PRIORITY 3
         * Useful combinations
         * ============================================================
         *
         * Combine ONE strong consonant/nasal change with ONE common
         * a/ae change.
         *
         * This gives useful candidates such as:
         *
         * da
         *   -> Dae
         *
         * than
         *   -> thaN
         *   -> thax
         *   -> thaen
         *
         * without recursively exploding every possible spelling.
         */

        variants.addAll(
            combinedHighPriorityVariants(
                typed
            )
        )

        /*
         * ============================================================
         * PRIORITY 4
         * Sanyaka / pre-nasalized clusters
         * ============================================================
         */

        variants.addAll(
            sanyakaClusterVariants(typed)
        )

        /*
         * ============================================================
         * PRIORITY 5
         * trailing vowel alternatives
         * ============================================================
         */

        if (
            typed.length == 1 ||
            isVowelOnlyWord(typed)
        ) {
            variants.addAll(
                standaloneVowelVariants(typed)
            )
        }

        if (typed.length >= 2) {

            variants.addAll(
                consonantVowelStemVariants(typed)
            )

            variants.addAll(
                vowelLengthVariants(typed)
            )
        }

        /*
         * ============================================================
         * PRIORITY 6
         * Broader internal vowel possibilities
         * ============================================================
         */

        if (typed.length >= 3) {

            variants.addAll(
                internalSyllableVowelVariants(
                    typed
                )
            )

            variants.addAll(
                vowelAeVariants(typed)
            )
        }

        /*
         * ============================================================
         * PRIORITY 7
         * Less common Sinhala homophones
         * ============================================================
         */

        variants.addAll(
            secondaryHomophoneVariants(
                typed
            )
        )

        /*
         * Never return the original spelling.
         */
        variants.remove(typed)

        /*
         * Prevent pathological alternatives.
         */
        return variants
            .asSequence()
            .filter {
                it.isNotBlank()
            }
            .filter {
                SinhalaSuggestionRules
                    .isReasonableSpellingVariant(
                        typed,
                        it,
                    )
            }
            .take(MAX_GENERATED_VARIANTS)
            .toCollection(
                linkedSetOf()
            )
    }

    /**
     * ================================================================
     * HIGH PRIORITY CONSONANTS
     * ================================================================
     *
     * Uses the Roman symbols already understood by SinglishConverter.
     *
     * d -> ද
     * D -> ඩ
     *
     * th -> ත
     * t  -> ට
     *
     * n -> න
     * N -> ණ
     *
     * l -> ල
     * L -> ළ
     */
    private fun highPriorityConsonantVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        /*
         * ------------------------------------------------------------
         * ද / ඩ
         * ------------------------------------------------------------
         *
         * This is intentionally before d <-> dh.
         *
         * "da" should immediately give both:
         *
         * da
         * Da
         */

        replaceEachSingleLetter(
            word = word,
            from = 'd',
            to = 'D',
            out = out,
            skipIfPartOf = listOf(
                "dh",
                "nd",
            ),
        )

        replaceEachSingleLetter(
            word = word,
            from = 'D',
            to = 'd',
            out = out,
        )

        /*
         * ------------------------------------------------------------
         * ත / ට
         * ------------------------------------------------------------
         *
         * Converter:
         *
         * th -> ත
         * t  -> ට
         */

        replaceEachDigraph(
            word = word,
            from = "th",
            to = "t",
            out = out,
        )

        replaceBareLetter(
            word = word,
            from = 't',
            replacement = "th",
            out = out,
            blockedFollower = 'h',
        )

        /*
         * ------------------------------------------------------------
         * න / ණ
         * ------------------------------------------------------------
         */

        replaceEachSingleLetter(
            word = word,
            from = 'n',
            to = 'N',
            out = out,
            skipIfPartOf = listOf(
                "ng",
                "nd",
            ),
        )

        replaceEachSingleLetter(
            word = word,
            from = 'N',
            to = 'n',
            out = out,
        )

        /*
         * ------------------------------------------------------------
         * ල / ළ
         * ------------------------------------------------------------
         */

        replaceEachSingleLetter(
            word = word,
            from = 'l',
            to = 'L',
            out = out,
        )

        replaceEachSingleLetter(
            word = word,
            from = 'L',
            to = 'l',
            out = out,
        )

        return out
    }

    /**
     * ================================================================
     * NASAL / ANUSVARA
     * ================================================================
     *
     * x is the converter's explicit anusvara token:
     *
     * x -> ං
     *
     * This gives us direct Sinhala branches instead of hoping that
     * "ng" happens to resolve correctly.
     */
    private fun highPriorityNasalVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val lower =
            word.lowercase()

        /*
         * ------------------------------------------------------------
         * Word-final n -> x
         * ------------------------------------------------------------
         *
         * than
         *
         * than -> thax
         *
         * allowing:
         *
         * තන් / තං
         */

        if (
            lower.endsWith("n") &&
            !lower.endsWith("ng")
        ) {

            out.add(
                word.dropLast(1) +
                    "x"
            )
        }

        /*
         * Existing explicit anusvara -> normal n alternative.
         */
        if (
            lower.endsWith("x")
        ) {

            out.add(
                word.dropLast(1) +
                    "n"
            )
        }

        /*
         * ------------------------------------------------------------
         * n before consonant -> x
         * ------------------------------------------------------------
         *
         * Very important for forms such as:
         *
         * sank...
         *
         * -> saxk...
         *
         * This allows the converter to produce:
         *
         * සංක්...
         */

        var i = 0

        while (
            i <
            word.length - 1
        ) {

            val current =
                lower[i]

            if (
                current == 'n'
            ) {

                /*
                 * Don't replace the n in explicit "ng".
                 */
                if (
                    lower.getOrNull(
                        i + 1
                    ) == 'g'
                ) {
                    i++
                    continue
                }

                val next =
                    lower.getOrNull(
                        i + 1
                    )

                if (
                    next != null &&
                    next !in ROMAN_VOWELS &&
                    next in ANUSVARA_FOLLOWERS
                ) {

                    out.add(
                        word.substring(
                            0,
                            i,
                        ) +
                            "x" +
                            word.substring(
                                i + 1
                            )
                    )
                }
            }

            i++
        }

        /*
         * ------------------------------------------------------------
         * ng <-> x
         * ------------------------------------------------------------
         */

        var ngIndex =
            lower.indexOf("ng")

        while (
            ngIndex >= 0
        ) {

            out.add(
                word.substring(
                    0,
                    ngIndex,
                ) +
                    "x" +
                    word.substring(
                        ngIndex + 2
                    )
            )

            ngIndex =
                lower.indexOf(
                    "ng",
                    ngIndex + 2,
                )
        }

        return out
    }

    /**
     * ================================================================
     * HIGH-PRIORITY a / ae
     * ================================================================
     *
     * Informal Singlish very frequently uses "a" for both:
     *
     * අ-style
     * ඇ-style
     *
     * We create changes one syllable at a time.
     */
    private fun highPriorityAeVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val lower =
            word.lowercase()

        /*
         * a -> ae
         *
         * Only a standalone short 'a', not one already inside:
         *
         * aa
         * ae
         */

        for (
            i in
            word.indices
        ) {

            if (
                lower[i] != 'a'
            ) {
                continue
            }

            val previous =
                lower.getOrNull(
                    i - 1
                )

            val next =
                lower.getOrNull(
                    i + 1
                )

            /*
             * Skip second/first character of aa/ae-like sequences.
             */
            if (
                previous == 'a' ||
                previous == 'e'
            ) {
                continue
            }

            if (
                next == 'a' ||
                next == 'e'
            ) {
                continue
            }

            /*
             * Most useful when it belongs to a consonant syllable.
             */
            if (
                previous != null &&
                previous !in ROMAN_VOWELS
            ) {

                out.add(
                    word.substring(
                        0,
                        i,
                    ) +
                        "ae" +
                        word.substring(
                            i + 1
                        )
                )
            }
        }

        /*
         * ae -> a
         */

        var index =
            lower.indexOf("ae")

        while (
            index >= 0
        ) {

            out.add(
                word.substring(
                    0,
                    index,
                ) +
                    "a" +
                    word.substring(
                        index + 2
                    )
            )

            index =
                lower.indexOf(
                    "ae",
                    index + 2,
                )
        }

        return out
    }

    /**
     * Combine only the BEST phonetic classes.
     *
     * This is deliberately shallow:
     *
     * original
     *   -> one consonant/nasal change
     *   -> optionally one a/ae change
     *
     * We do NOT recursively feed every resulting variant back through
     * every rule.
     */
    private fun combinedHighPriorityVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val strongBase =
            linkedSetOf<String>()

        strongBase.addAll(
            highPriorityConsonantVariants(
                word
            )
        )

        strongBase.addAll(
            highPriorityNasalVariants(
                word
            )
        )

        /*
         * Only combine the first few strong alternatives.
         *
         * This keeps latency predictable.
         */
        for (
            base in
            strongBase.take(
                MAX_COMBINATION_BASES
            )
        ) {

            for (
                aeVariant in
                highPriorityAeVariants(
                    base
                ).take(
                    MAX_AE_COMBINATIONS_PER_BASE
                )
            ) {

                out.add(
                    aeVariant
                )
            }
        }

        /*
         * Also apply consonant/nasal ambiguity to the strongest
         * a/ae variant.
         *
         * Example:
         *
         * than
         * -> thaen
         * -> thaeN / thaex
         */

        for (
            aeBase in
            highPriorityAeVariants(
                word
            ).take(
                MAX_AE_BASES
            )
        ) {

            highPriorityConsonantVariants(
                aeBase
            )
                .take(
                    MAX_SECONDARY_COMBINATIONS
                )
                .forEach {
                    out.add(it)
                }

            highPriorityNasalVariants(
                aeBase
            )
                .take(
                    MAX_SECONDARY_COMBINATIONS
                )
                .forEach {
                    out.add(it)
                }
        }

        return out
    }

    /**
     * ================================================================
     * SANYAKA / PRE-NASALIZED CLUSTERS
     * ================================================================
     */
    private fun sanyakaClusterVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val lower =
            word.lowercase()

        /*
         * nd is understood by SinglishConverter as SANYAKA_DHA.
         *
         * nD / Nd can represent SANYAKA_DA.
         */

        var index =
            lower.indexOf("nd")

        while (
            index >= 0
        ) {

            out.add(
                word.substring(
                    0,
                    index,
                ) +
                    "nD" +
                    word.substring(
                        index + 2
                    )
            )

            index =
                lower.indexOf(
                    "nd",
                    index + 2,
                )
        }

        /*
         * mb is already understood as a sanyaka cluster by converter.
         *
         * Keep a normal m+b spelling possibility available by allowing
         * a separating form only when explicitly useful later.
         *
         * No expansion needed here.
         */

        return out
    }

    /**
     * ================================================================
     * STANDALONE VOWELS
     * ================================================================
     */
    private fun standaloneVowelVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        when (
            word.lowercase()
        ) {

            "a" -> {
                out.add("ae")
                out.add("aa")
                out.add("aee")
            }

            "ae" -> {
                out.add("a")
                out.add("aee")
                out.add("aa")
            }

            "aa" -> {
                out.add("a")
                out.add("ae")
                out.add("aee")
            }

            "aee" -> {
                out.add("ae")
                out.add("aa")
                out.add("a")
            }

            "i" ->
                out.add("ii")

            "ii" ->
                out.add("i")

            "u" ->
                out.add("uu")

            "uu" ->
                out.add("u")

            "e" ->
                out.add("ee")

            "ee" ->
                out.add("e")

            "o" ->
                out.add("oo")

            "oo" ->
                out.add("o")
        }

        return out
    }

    /**
     * ================================================================
     * TRAILING VOWEL
     * ================================================================
     */
    private fun consonantVowelStemVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val lower =
            word.lowercase()

        when {

            lower.endsWith("aee") &&
                lower.length > 3 -> {

                val stem =
                    word.dropLast(3)

                if (
                    hasConsonantStem(
                        stem
                    )
                ) {

                    out.add(
                        stem + "ae"
                    )

                    out.add(
                        stem + "aa"
                    )

                    out.add(
                        stem + "a"
                    )
                }
            }

            lower.endsWith("ae") &&
                lower.length > 2 -> {

                val stem =
                    word.dropLast(2)

                if (
                    hasConsonantStem(
                        stem
                    )
                ) {

                    out.add(
                        stem + "a"
                    )

                    out.add(
                        stem + "aee"
                    )

                    out.add(
                        stem + "aa"
                    )
                }
            }

            lower.endsWith("aa") &&
                lower.length > 2 -> {

                val stem =
                    word.dropLast(2)

                if (
                    hasConsonantStem(
                        stem
                    )
                ) {

                    out.add(
                        stem + "a"
                    )

                    out.add(
                        stem + "ae"
                    )

                    out.add(
                        stem + "aee"
                    )
                }
            }

            lower.endsWith("a") &&
                !lower.endsWith("aa") &&
                !lower.endsWith("ae") &&
                lower.length > 1 -> {

                val stem =
                    word.dropLast(1)

                if (
                    hasConsonantStem(
                        stem
                    )
                ) {

                    /*
                     * æ is more useful for live Sinhala ambiguity than
                     * broad long-vowel expansion, so put it first.
                     */

                    out.add(
                        stem + "ae"
                    )

                    out.add(
                        stem
                    )

                    out.add(
                        stem + "aa"
                    )

                    out.add(
                        stem + "aee"
                    )
                }
            }
        }

        return out
    }

    /**
     * Short/long vowel alternatives.
     */
    private fun vowelLengthVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        toggleSuffix(
            word,
            "oo",
            "o",
            out,
        )

        toggleSuffix(
            word,
            "o",
            "oo",
            out,
        ) {
            !word.lowercase()
                .endsWith("oo")
        }

        toggleSuffix(
            word,
            "ee",
            "e",
            out,
        )

        toggleSuffix(
            word,
            "e",
            "ee",
            out,
        ) {

            val lower =
                word.lowercase()

            !lower.endsWith("ee") &&
                !lower.endsWith("ae")
        }

        toggleSuffix(
            word,
            "ii",
            "i",
            out,
        )

        toggleSuffix(
            word,
            "i",
            "ii",
            out,
        ) {
            !word.lowercase()
                .endsWith("ii")
        }

        toggleSuffix(
            word,
            "uu",
            "u",
            out,
        )

        toggleSuffix(
            word,
            "u",
            "uu",
            out,
        ) {
            !word.lowercase()
                .endsWith("uu")
        }

        return out
    }

    /**
     * Limited internal vowel length ambiguity.
     *
     * ONE change per candidate.
     */
    private fun internalSyllableVowelVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        val lower =
            word.lowercase()

        for (
            i in
            1 until
                word.length - 1
        ) {

            val previous =
                lower[i - 1]

            val current =
                lower[i]

            val next =
                lower[i + 1]

            if (
                previous in ROMAN_VOWELS ||
                next in ROMAN_VOWELS
            ) {
                continue
            }

            when (
                current
            ) {

                'a' -> {

                    if (
                        lower.getOrNull(
                            i + 1
                        ) != 'a'
                    ) {

                        out.add(
                            word.substring(
                                0,
                                i,
                            ) +
                                "aa" +
                                word.substring(
                                    i + 1
                                )
                        )
                    }
                }

                'i' -> {

                    if (
                        lower.getOrNull(
                            i + 1
                        ) != 'i'
                    ) {

                        out.add(
                            word.substring(
                                0,
                                i,
                            ) +
                                "ii" +
                                word.substring(
                                    i + 1
                                )
                        )
                    }
                }

                'u' -> {

                    if (
                        lower.getOrNull(
                            i + 1
                        ) != 'u'
                    ) {

                        out.add(
                            word.substring(
                                0,
                                i,
                            ) +
                                "uu" +
                                word.substring(
                                    i + 1
                                )
                        )
                    }
                }

                'e' -> {

                    if (
                        lower.getOrNull(
                            i + 1
                        ) != 'e'
                    ) {

                        out.add(
                            word.substring(
                                0,
                                i,
                            ) +
                                "ee" +
                                word.substring(
                                    i + 1
                                )
                        )
                    }
                }

                'o' -> {

                    if (
                        lower.getOrNull(
                            i + 1
                        ) != 'o'
                    ) {

                        out.add(
                            word.substring(
                                0,
                                i,
                            ) +
                                "oo" +
                                word.substring(
                                    i + 1
                                )
                        )
                    }
                }
            }

            if (
                out.size >=
                MAX_INTERNAL_VOWEL_VARIANTS
            ) {
                break
            }
        }

        return out
    }

    /**
     * ae / aa / aee alternatives.
     *
     * Kept lower priority than simple a -> ae.
     */
    private fun vowelAeVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        replaceFirstOccurrence(
            word,
            "aee",
            "ae",
            out,
        )

        replaceFirstOccurrence(
            word,
            "aee",
            "aa",
            out,
        )

        replaceFirstOccurrence(
            word,
            "ae",
            "a",
            out,
        )

        replaceFirstOccurrence(
            word,
            "ae",
            "aee",
            out,
        )

        replaceFirstOccurrence(
            word,
            "aa",
            "a",
            out,
        )

        replaceFirstOccurrence(
            word,
            "aa",
            "ae",
            out,
        )

        return out
    }

    /**
     * Lower-priority consonant homophones.
     *
     * These remain useful, but they should never consume the early
     * real-time slots needed for:
     *
     * d/D
     * t/th
     * n/N/x
     * a/ae
     */
    private fun secondaryHomophoneVariants(
        word: String,
    ): Set<String> {

        val out =
            linkedSetOf<String>()

        /*
         * Aspirated / related consonants.
         */

        replaceFirstOccurrence(
            word,
            "kh",
            "K",
            out,
        )

        replaceFirstOccurrence(
            word,
            "K",
            "kh",
            out,
        )

        replaceFirstOccurrence(
            word,
            "gh",
            "G",
            out,
        )

        replaceFirstOccurrence(
            word,
            "G",
            "gh",
            out,
        )

        replaceFirstOccurrence(
            word,
            "ph",
            "P",
            out,
        )

        replaceFirstOccurrence(
            word,
            "P",
            "ph",
            out,
        )

        replaceFirstOccurrence(
            word,
            "bh",
            "B",
            out,
        )

        replaceFirstOccurrence(
            word,
            "B",
            "bh",
            out,
        )

        /*
         * ශ / ෂ / ස family
         */

        replaceFirstOccurrence(
            word,
            "sh",
            "Sh",
            out,
        )

        replaceFirstOccurrence(
            word,
            "Sh",
            "sh",
            out,
        )

        replaceEachSingleLetter(
            word = word,
            from = 's',
            to = 'S',
            out = out,
            skipIfPartOf = listOf(
                "sh",
            ),
        )

        replaceEachSingleLetter(
            word = word,
            from = 'S',
            to = 's',
            out = out,
        )

        /*
         * ජ / ඣ-style existing converter distinction
         */

        replaceEachSingleLetter(
            word = word,
            from = 'j',
            to = 'J',
            out = out,
            skipIfPartOf = listOf(
                "jh",
            ),
        )

        replaceEachSingleLetter(
            word = word,
            from = 'J',
            to = 'j',
            out = out,
        )

        return out
    }

    private fun hasConsonantStem(
        stem: String,
    ): Boolean {

        if (
            stem.isEmpty()
        ) {
            return false
        }

        return stem
            .last()
            .lowercaseChar() !in
            ROMAN_VOWELS
    }

    private fun isVowelOnlyWord(
        word: String,
    ): Boolean {

        return word.all {
            it.lowercaseChar() in
                ROMAN_VOWELS
        }
    }

    /**
     * Replace each occurrence of one single Roman symbol.
     *
     * Generates ONE changed spelling per occurrence.
     */
    private fun replaceEachSingleLetter(
        word: String,
        from: Char,
        to: Char,
        out: MutableSet<String>,
        skipIfPartOf: List<String> =
            emptyList(),
    ) {

        for (
            i in
            word.indices
        ) {

            if (
                word[i] != from
            ) {
                continue
            }

            var blocked =
                false

            for (
                sequence in
                skipIfPartOf
            ) {

                if (
                    occurrenceBelongsToSequence(
                        word = word,
                        index = i,
                        sequence = sequence,
                    )
                ) {

                    blocked =
                        true

                    break
                }
            }

            if (blocked) {
                continue
            }

            out.add(
                word.substring(
                    0,
                    i,
                ) +
                    to +
                    word.substring(
                        i + 1
                    )
            )
        }
    }

    private fun replaceBareLetter(
        word: String,
        from: Char,
        replacement: String,
        out: MutableSet<String>,
        blockedFollower: Char,
    ) {

        for (
            i in
            word.indices
        ) {

            if (
                !word[i]
                    .equals(
                        from,
                        ignoreCase = false,
                    )
            ) {
                continue
            }

            if (
                word.getOrNull(
                    i + 1
                ) == blockedFollower
            ) {
                continue
            }

            out.add(
                word.substring(
                    0,
                    i,
                ) +
                    replacement +
                    word.substring(
                        i + 1
                    )
            )
        }
    }

    private fun replaceEachDigraph(
        word: String,
        from: String,
        to: String,
        out: MutableSet<String>,
    ) {

        var start =
            0

        while (
            start <=
            word.length -
                from.length
        ) {

            val index =
                word.indexOf(
                    from,
                    startIndex = start,
                    ignoreCase = true,
                )

            if (
                index < 0
            ) {
                break
            }

            out.add(
                word.substring(
                    0,
                    index,
                ) +
                    to +
                    word.substring(
                        index +
                            from.length
                    )
            )

            start =
                index +
                    from.length
        }
    }

    private fun occurrenceBelongsToSequence(
        word: String,
        index: Int,
        sequence: String,
    ): Boolean {

        val lower =
            word.lowercase()

        val target =
            sequence.lowercase()

        /*
         * Could this character be anywhere inside the sequence?
         */
        for (
            offset in
            target.indices
        ) {

            val start =
                index -
                    offset

            if (
                start < 0
            ) {
                continue
            }

            if (
                start +
                    target.length >
                lower.length
            ) {
                continue
            }

            if (
                lower.regionMatches(
                    start,
                    target,
                    0,
                    target.length,
                )
            ) {
                return true
            }
        }

        return false
    }

    private inline fun toggleSuffix(
        word: String,
        from: String,
        to: String,
        out: MutableSet<String>,
        extra: () -> Boolean = {
            true
        },
    ) {

        if (
            extra() &&
            word.endsWith(
                from,
                ignoreCase = true,
            )
        ) {

            out.add(
                word.dropLast(
                    from.length
                ) +
                    to
            )
        }
    }

    private fun replaceFirstOccurrence(
        word: String,
        from: String,
        to: String,
        out: MutableSet<String>,
    ) {

        val index =
            word.indexOf(
                from,
                ignoreCase = false,
            )

        if (
            index < 0
        ) {
            return
        }

        out.add(
            word.replaceRange(
                index,
                index +
                    from.length,
                to,
            )
        )
    }

    private const val ROMAN_VOWELS =
        "aeiou"

    /**
     * Consonants after which informal "n" is often compatible with
     * anusvara in Sinhala spelling.
     *
     * Keep this deliberately conservative.
     */
    private const val ANUSVARA_FOLLOWERS =
        "kgcjtdpbmsh"

    /**
     * The engine currently takes only the first handful for live corpus
     * searching. We can generate somewhat more here for other callers,
     * while still keeping this method bounded.
     */
    private const val MAX_GENERATED_VARIANTS =
        24

    private const val MAX_COMBINATION_BASES =
        4

    private const val MAX_AE_COMBINATIONS_PER_BASE =
        2

    private const val MAX_AE_BASES =
        2

    private const val MAX_SECONDARY_COMBINATIONS =
        2

    private const val MAX_INTERNAL_VOWEL_VARIANTS =
        4
}
