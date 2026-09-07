package com.personal.sinhalakeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AlternateSinhalaReadings now converts one roman spelling only (+ trailing pillam).
 * Full ambiguity expansion lives in SinglishEngine.
 */
class AlternateSinhalaReadingsTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    @Test
    fun konara_includesDirectReading() {
        val readings = AlternateSinhalaReadings.forRoman("konara")
        assertTrue(readings.contains(c("konara")))
        assertFalse(readings.any { it.contains(' ') })
    }

    @Test
    fun ko_includesShortAndLongO() {
        val readings = AlternateSinhalaReadings.forRoman("ko")
        assertTrue(readings.contains(c("ko"))) // කො
        assertTrue(readings.contains(c("koo"))) // කෝ
    }

    @Test
    fun patiyo_includesDirectReading() {
        val readings = AlternateSinhalaReadings.forRoman("patiyo")
        assertTrue(readings.contains(SinglishConverter.convert("patiyo")))
        assertTrue(SinglishAmbiguityVariants.liveVariants("patiyo").contains("paetiyo"))
    }

    @Test
    fun handa_includesDirectReading() {
        val readings = AlternateSinhalaReadings.forRoman("handa")
        assertTrue(readings.contains(c("handa"))) // හඳ
        assertFalse(readings.any { it.contains(' ') })
    }
}
