package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinhalaSuggestionRankerTest {

    @Test
    fun rank_prefersHigherCorpusFrequency() {
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 6,
            personal = emptyList(),
            corpusFrequencies = mapOf(
                "low" to 120,
                "high" to 98_450,
                "mid" to 4_500,
            ),
            homophoneReadings = listOf("low", "high", "mid"),
            limit = 3,
        )
        assertEquals(listOf("high", "mid", "low"), ranked)
    }

    @Test
    fun rank_personalHistoryOutranksCorpus() {
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 4,
            personal = listOf(SuggestionCandidate("mine", "mine")),
            corpusFrequencies = mapOf("common" to 500_000),
            homophoneReadings = listOf("common", "mine"),
            limit = 2,
        )
        assertEquals("mine", ranked.first())
    }

    @Test
    fun rank_dropsZeroFrequencyWhenCorpusHasHits() {
        val ranked = SinhalaSuggestionRanker.rank(
            typedRomanLength = 5,
            personal = emptyList(),
            corpusFrequencies = mapOf("real" to 2_000),
            homophoneReadings = listOf("noise", "real"),
            limit = 5,
        )
        assertTrue(ranked.contains("real"))
        assertTrue(!ranked.contains("noise"))
    }
}
