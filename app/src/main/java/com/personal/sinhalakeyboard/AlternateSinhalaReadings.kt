package com.personal.sinhalakeyboard

/** All distinct Sinhala spellings for one Singlish prefix (homophones / pillam variants). */
object AlternateSinhalaReadings {

    fun forRoman(roman: String): List<String> {
        val readings = linkedSetOf<String>()
        val primary = SinglishConverter.convert(roman)
        if (primary.isNotEmpty() && !primary.contains(' ')) readings.add(primary)
        for (variant in SinglishAmbiguityVariants.liveVariants(roman)) {
            if (variant.contains(' ')) continue
            val sinhala = SinglishConverter.convert(variant)
            if (sinhala.isNotEmpty() && !sinhala.contains(' ')) readings.add(sinhala)
        }
        return readings.toList()
    }
}
