package com.personal.sinhalakeyboard

/** All distinct Sinhala spellings for one Singlish prefix (homophones / pillam variants). */
object AlternateSinhalaReadings {

    fun forRoman(roman: String): List<String> {
        val readings = linkedSetOf<String>()
        addRomanForm(readings, roman)

        for (variant in SinglishAmbiguityVariants.liveVariants(roman)) {
            if (variant.contains(' ')) continue
            addRomanForm(readings, variant)
        }

        addConsonantVowelPillamFallback(readings, roman)
        return readings.toList()
    }

    private fun addRomanForm(readings: MutableSet<String>, roman: String) {
        val sinhala = SinglishConverter.convert(roman)
        if (sinhala.isNotEmpty() && !sinhala.contains(' ')) readings.add(sinhala)
    }

    /** ko→koo, ka→kaa, etc. even when variant generators miss a form. */
    private fun addConsonantVowelPillamFallback(readings: MutableSet<String>, roman: String) {
        if (roman.length < 2) return
        val lower = roman.lowercase()
        val toggles = listOf(
            Triple("oo", "o", false),
            Triple("o", "oo", true),
            Triple("ee", "e", false),
            Triple("e", "ee", true),
            Triple("aa", "a", false),
            Triple("a", "aa", true),
            Triple("ii", "i", false),
            Triple("i", "ii", true),
            Triple("uu", "u", false),
            Triple("u", "uu", true),
        )
        for ((from, to, skipIfDouble) in toggles) {
            if (!lower.endsWith(from)) continue
            if (skipIfDouble && lower.endsWith(from + from.first())) continue
            val stem = roman.dropLast(from.length)
            if (stem.isEmpty() || stem.last().lowercaseChar() in "aeiou") continue
            addRomanForm(readings, stem + to)
        }
    }
}
