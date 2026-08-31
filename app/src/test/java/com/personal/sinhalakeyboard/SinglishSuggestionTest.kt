package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglishSuggestionTest {

    private val KA = "\u0D9A"
    private val KO = "$KA\u0DDC" // කො

    @Test
    fun romanVariantsForShortPrefixAreEmpty() {
        assertTrue(SinhalaSuggestionRules.romanVariants("ko").isEmpty())
        assertTrue(SinhalaSuggestionRules.romanVariants("ka").isEmpty())
    }

    @Test
    fun romanVariantsDoNotGrowExponentially() {
        for (word in listOf("koh", "mama", "hari", "thaththa", "kohomada")) {
            for (variant in SinhalaSuggestionRules.romanVariants(word)) {
                assertTrue("Variant too long for $word: $variant", variant.length <= word.length + 2)
            }
        }
    }

    @Test
    fun longRepeatingOutputIsRejected() {
        val garbage = KO + "\u0D95".repeat(200)
        assertFalse(SinhalaSuggestionRules.isReasonableSinhalaSuggestion(garbage, typedRomanLength = 2))
    }

    @Test
    fun exponentialVowelRomanKeysAreRejected() {
        val key = "k" + "o".repeat(64)
        assertFalse(SinhalaSuggestionRules.isReasonableRomanKey(key))
    }

    @Test
    fun normalSinhalaSuggestionsAreAccepted() {
        assertTrue(SinhalaSuggestionRules.isReasonableSinhalaSuggestion(KO, typedRomanLength = 2))
        assertTrue(
            SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                "\u0D9A\u0DDC\u0DC4\u0DDC\u0DB8\u0DAF",
                typedRomanLength = 2,
            ),
        )
        assertTrue(
            SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                SinglishConverter.convert("mama"),
                typedRomanLength = 4,
            ),
        )
        assertTrue(
            SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                SinglishConverter.convert("thaththa"),
                typedRomanLength = 7,
            ),
        )
    }

    @Test
    fun convertLivePrefixStaysWithinFilter() {
        val prefixes = listOf("k", "ko", "ka", "m", "ma", "mam", "mama", "ha", "hari", "th", "tha", "thaththa")
        for (prefix in prefixes) {
            val sinhala = SinglishConverter.convert(prefix)
            if (sinhala.isEmpty()) continue
            assertTrue(
                "Bad live convert for '$prefix': len=${sinhala.length} text=$sinhala",
                SinhalaSuggestionRules.isReasonableSinhalaSuggestion(sinhala, prefix.length),
            )
        }
    }

    @Test
    fun oldStyleLongVariantsAreFilteredOut() {
        val cases = listOf(
            "koooooooo" to 2,
            "koooooooooooooooo" to 2,
            "maaaaaaaa" to 4,
        )
        for ((variant, typedLen) in cases) {
            val sinhala = SinglishConverter.convert(variant)
            assertFalse(
                "Should reject convert($variant) len=${sinhala.length}",
                SinhalaSuggestionRules.isReasonableSinhalaSuggestion(sinhala, typedRomanLength = typedLen),
            )
        }
    }

    @Test
    fun corpusLongWordsAreAcceptedWhenFlagged() {
        val longWord = "\u0D85\u0DB1\u0DD2\u0DC0\u0DCF\u0DBB\u0DCA\u0DBA\u0DB1\u0D9C\u0DDA"
        assertFalse(SinhalaSuggestionRules.isReasonableSinhalaSuggestion(longWord, typedRomanLength = 2))
        assertTrue(
            SinhalaSuggestionRules.isReasonableSinhalaSuggestion(
                longWord,
                typedRomanLength = 2,
                fromCorpus = true,
            ),
        )
    }

    @Test
    fun convertKoIsClean() {
        assertEquals(KO, SinglishConverter.convert("ko"))
    }
}
