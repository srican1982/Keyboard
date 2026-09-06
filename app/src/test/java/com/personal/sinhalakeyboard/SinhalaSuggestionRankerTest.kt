package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinhalaSuggestionRankerTest {

    @Test
    fun rank_prefersHigherCorpusFrequency() {
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 6,
            corpusFrequencies = mapOf(
                "\u0D8A\u0DA7\u0DD2\u0DBA\u0DDA" to 120,
                "\u0D8A\u0D87\u0DA7\u0DD2\u0DBA\u0DDA" to 98_450,
                "\u0D8A\u0DA7\u0DD2" to 4_500,
            ),
            personalCounts = emptyMap(),
            homophoneReadings = listOf(
                "\u0D8A\u0DA7\u0DD2\u0DBA\u0DDA",
                "\u0D8A\u0D87\u0DA7\u0DD2\u0DBA\u0DDA",
                "\u0D8A\u0DA7\u0DD2",
            ),
            limit = 3,
        )
        assertEquals("\u0D8A\u0D87\u0DA7\u0DD2\u0DBA\u0DDA", ranked.first())
    }

    @Test
    fun rank_personalHistoryOutranksCorpus() {
        val word = "\u0D8A\u0D87\u0DA7\u0DD2\u0DBA\u0DDA"
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 4,
            corpusFrequencies = mapOf("\u0D8A\u0DA7\u0DD2\u0DBA\u0DDA" to 500_000),
            personalCounts = mapOf(word to 3),
            homophoneReadings = listOf("\u0D8A\u0DA7\u0DD2\u0DBA\u0DDA", word),
            limit = 2,
        )
        assertEquals(word, ranked.first())
    }

    @Test
    fun rank_dropsZeroScoreWhenCorpusHasHits() {
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 5,
            corpusFrequencies = mapOf("\u0D8A\u0DA7\u0DD2" to 2_000),
            personalCounts = emptyMap(),
            homophoneReadings = listOf("\u0D8A\u0DA7\u0DD2", "\u0D8A\u0D87"),
            limit = 5,
        )
        assertTrue(ranked.contains("\u0D8A\u0DA7\u0DD2"))
        assertTrue(!ranked.contains("\u0D8A\u0D87"))
    }
}
