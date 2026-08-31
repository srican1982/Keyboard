package com.personal.sinhalakeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternateSinhalaReadingsTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    @Test
    fun konara_includesPillamHomophones() {
        val readings = AlternateSinhalaReadings.forRoman("konara")
        assertTrue(readings.contains(c("konara")))
        assertTrue(readings.contains(c("koonara")))
        assertTrue(readings.contains(c("koonaara")))
        assertTrue(readings.contains(c("kooNaara")))
        assertFalse(readings.any { it.contains(' ') })
    }

    @Test
    fun ko_includesShortAndLongO() {
        val readings = AlternateSinhalaReadings.forRoman("ko")
        assertTrue(readings.contains(c("ko"))) // කො
        assertTrue(readings.contains(c("koo"))) // කෝ
        assertTrue(SinhalaSuggestionRules.isReasonableSinhalaSuggestion(c("ko"), 2))
        assertTrue(SinhalaSuggestionRules.isReasonableSinhalaSuggestion(c("koo"), 2))
    }

    @Test
    fun handa_includesAllTripleReadings() {
        val readings = AlternateSinhalaReadings.forRoman("handa")
        assertTrue(readings.contains(c("handa"))) // හඳ
        assertTrue(readings.contains(c("hanDa"))) // හඬ
        assertTrue(readings.contains(c("haendha"))) // හැන්ද
        assertFalse(readings.any { it.contains(' ') })
    }
}
