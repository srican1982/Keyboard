package com.personal.sinhalakeyboard

import org.junit.Assert.*
import org.junit.Test

class SinhalaPhoneticIndexTest {
    @Test fun pronunciationFamiliesAcrossWords() {
        val families = mapOf(
            "handa" to listOf("හඳ", "හඬ", "හැන්ද"),
            "ko" to listOf("කො", "කෝ"),
            "konara" to listOf("කෝනර", "කෝනාර", "කෝණාර"),
            "patiyo" to listOf("පටියෝ", "පැටියෝ"),
            "than" to listOf("තන්", "තං", "තැන්"),
            "nidida" to listOf("නිදිද"),
            "sambhawithawa" to listOf("සම්භාවිතාව"),
            "ka" to listOf("ක", "කා", "කැ", "කෑ"),
            "mal" to listOf("මල්", "මැල්", "මාල්"),
        )
        for ((roman, words) in families) for (word in words) {
            assertEquals("$roman -> $word", SinhalaPronunciation.roman(roman), SinhalaPronunciation.sinhala(word))
        }
    }

    @Test fun exactMatchesBeatCommonCompletionsAndHistoryBreaksTies() {
        val index = SinhalaPhoneticIndex(listOf(
            SinhalaPhoneticIndex.Entry("පටියෝ", 2),
            SinhalaPhoneticIndex.Entry("පැටියෝ", 20),
            SinhalaPhoneticIndex.Entry("පටියෝම", 100000),
        ))
        val matches = index.query("patiyo")
        assertEquals(listOf("පැටියෝ", "පටියෝ", "පටියෝම"), SinhalaPhoneticIndex.rank(matches, emptyMap(), 3))
        assertEquals("පටියෝ", SinhalaPhoneticIndex.rank(matches, mapOf("පටියෝ" to 2), 3).first())
    }

    @Test fun prefixResultsUseFrequencyNotUnicodeOrder() {
        val index = SinhalaPhoneticIndex(listOf(
            SinhalaPhoneticIndex.Entry("කම", 1), SinhalaPhoneticIndex.Entry("කෑම", 100),
        ))
        assertEquals("කෑම", SinhalaPhoneticIndex.rank(index.query("k"), emptyMap(), 2).first())
        assertTrue(index.query("hello!").isEmpty())
        assertEquals("", SinhalaPronunciation.sinhala("කhello"))
    }
}
