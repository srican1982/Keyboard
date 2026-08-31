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
    fun handa_noSpacedReadings() {
        val readings = AlternateSinhalaReadings.forRoman("handa")
        assertTrue(readings.contains(c("haendha")))
        assertFalse(readings.any { it.contains(' ') })
    }
}
