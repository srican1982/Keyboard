package com.personal.sinhalakeyboard

/**
 * Produces Sinhala readings for one Roman spelling.
 *
 * IMPORTANT:
 * Ambiguity expansion is handled by SinglishEngine.
 *
 * This class should NOT call SinglishAmbiguityVariants.liveVariants()
 * again, otherwise variants get expanded multiple times and produce
 * too many irrelevant Sinhala suggestions.
 */
object AlternateSinhalaReadings {

    /**
     * Convert one Roman spelling into its useful Sinhala readings.
     *
     * The direct conversion is always first.
     * A small pillam fallback is added only when useful.
     */
    fun forRoman(roman: String): List<String> {
        val cleaned = roman.trim()

        if (cleaned.isEmpty()) {
            return emptyList()
        }

        val readings = linkedSetOf<String>()

        // 1. Always keep the direct transliteration first.
        addRomanForm(
            readings = readings,
            roman = cleaned,
        )

        // 2. Add only a small trailing-vowel fallback.
        //
        // Example:
        // ko  -> කො
        // koo -> කෝ
        //
        // ka  -> ක
        // kaa -> කා
        //
        // We do NOT run the full ambiguity engine here.
        addConsonantVowelPillamFallback(
            readings = readings,
            roman = cleaned,
        )

        return readings.toList()
    }

    /**
     * Converts a Roman spelling into Sinhala and adds it if valid.
     */
    private fun addRomanForm(
        readings: MutableSet<String>,
        roman: String,
    ) {
        if (roman.isBlank()) {
            return
        }

        val sinhala = SinglishConverter.convert(roman)

        if (sinhala.isEmpty()) {
            return
        }

        // Live word suggestions should be one word only.
        if (sinhala.contains(' ')) {
            return
        }

        if (!containsSinhalaScript(sinhala)) {
            return
        }

        readings.add(sinhala)
    }

    /**
     * Small fallback for common trailing vowel-length ambiguity.
     *
     * This intentionally handles ONLY the final vowel.
     *
     * Examples:
     *
     * ka   <-> kaa
     * ko   <-> koo
     * ke   <-> kee
     * ki   <-> kii
     * ku   <-> kuu
     *
     * Full spelling ambiguity is handled by SinglishAmbiguityVariants
     * in SinglishEngine, so it must not be repeated here.
     */
    private fun addConsonantVowelPillamFallback(
        readings: MutableSet<String>,
        roman: String,
    ) {
        if (roman.length < 2) {
            return
        }

        val lower = roman.lowercase()

        /*
         * Check longer forms before shorter forms.
         *
         * For example "koo" must match "oo" before "o".
         */
        val toggles = listOf(
            VowelToggle(
                from = "aee",
                to = "ae",
            ),
            VowelToggle(
                from = "aa",
                to = "a",
            ),
            VowelToggle(
                from = "oo",
                to = "o",
            ),
            VowelToggle(
                from = "ee",
                to = "e",
            ),
            VowelToggle(
                from = "ii",
                to = "i",
            ),
            VowelToggle(
                from = "uu",
                to = "u",
            ),

            VowelToggle(
                from = "ae",
                to = "aee",
            ),
            VowelToggle(
                from = "a",
                to = "aa",
            ),
            VowelToggle(
                from = "o",
                to = "oo",
            ),
            VowelToggle(
                from = "e",
                to = "ee",
            ),
            VowelToggle(
                from = "i",
                to = "ii",
            ),
            VowelToggle(
                from = "u",
                to = "uu",
            ),
        )

        for (toggle in toggles) {

            if (!lower.endsWith(toggle.from)) {
                continue
            }

            /*
             * Avoid treating the short vowel inside a long vowel
             * as another independent match.
             *
             * Example:
             *
             * "koo" should use oo -> o.
             * It should NOT also use o -> oo on the final character.
             */
            if (
                toggle.from.length == 1 &&
                isAlreadyLongVowelEnding(lower, toggle.from)
            ) {
                continue
            }

            val stem = roman.dropLast(toggle.from.length)

            if (stem.isEmpty()) {
                continue
            }

            /*
             * Only apply this fallback when the vowel follows a
             * consonant stem.
             *
             * This prevents strange transformations on pure vowel
             * sequences.
             */
            val lastStemChar = stem.last().lowercaseChar()

            if (lastStemChar in "aeiou") {
                continue
            }

            addRomanForm(
                readings = readings,
                roman = stem + toggle.to,
            )
        }
    }

    /**
     * True when a single-vowel suffix is already part of a
     * doubled/longer vowel ending.
     */
    private fun isAlreadyLongVowelEnding(
        word: String,
        shortVowel: String,
    ): Boolean {

        return when (shortVowel) {

            "a" ->
                word.endsWith("aa") ||
                    word.endsWith("ae") ||
                    word.endsWith("aee")

            "e" ->
                word.endsWith("ee") ||
                    word.endsWith("ae")

            "i" ->
                word.endsWith("ii")

            "o" ->
                word.endsWith("oo")

            "u" ->
                word.endsWith("uu")

            else ->
                false
        }
    }

    private fun containsSinhalaScript(text: String): Boolean {
        return text.any { char ->
            char.code in 0x0D80..0x0DFF
        }
    }

    private data class VowelToggle(
        val from: String,
        val to: String,
    )
}
