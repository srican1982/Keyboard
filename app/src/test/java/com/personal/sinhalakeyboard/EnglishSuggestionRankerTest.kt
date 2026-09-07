package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishSuggestionRankerTest {

    @Test
    fun rank_personalHistoryFirst() {
        val ranked = EnglishSuggestionRanker.rank(
            prefix = "hel",
            candidates = listOf(
                SuggestionCandidate("hello", "hello"),
                SuggestionCandidate("help", "help"),
            ),
            personalCounts = mapOf("help" to 5, "hello" to 1),
            limit = 2,
        )
        assertEquals("help", ranked.first().commitText)
    }

    @Test
    fun rank_filtersSinhalaScript() {
        val ranked = EnglishSuggestionRanker.rank(
            prefix = "te",
            candidates = listOf(
                SuggestionCandidate("test", "test"),
                SuggestionCandidate("\u0DAD\u0DDA\u0DC3\u0DCA\u0DAD\u0DDA", "\u0DAD\u0DDA\u0DC3\u0DCA\u0DAD\u0DDA"),
            ),
            personalCounts = emptyMap(),
            limit = 4,
        )
        assertEquals(1, ranked.size)
        assertEquals("test", ranked.first().commitText)
    }

    @Test
    fun isEnglishOnly_rejectsSinhala() {
        assertTrue(EnglishSuggestionRanker.isEnglishOnly("hello"))
        assertTrue(!EnglishSuggestionRanker.isEnglishOnly("\u0D8A\u0DA7\u0DD2"))
    }

    @Test
    fun isEnglishCloudSuggestion_allowsPhrases() {
        assertTrue(EnglishSuggestionRanker.isEnglishCloudSuggestion("tell you something"))
        assertTrue(!EnglishSuggestionRanker.isEnglishCloudSuggestion("\u0D8A\u0DA7\u0DD2"))
    }
}
