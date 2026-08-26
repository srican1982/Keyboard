package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Anusvara, visarga, and pre-nasalized (sanyaka) consonants. */
class SinglishSpecialMarksTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    private val ANUSVARA = "\u0D82"
    private val VISARGA = "\u0D83"

    @Test fun anusvara_ngBeforeConsonant() {
        assertEquals("\u0DC3$ANUSVARA\u0D9A", c("sangka")) // සංක
    }

    @Test fun anusvara_xShortcut() {
        assertEquals("\u0DC3$ANUSVARA\u0DAD", c("saxtha")) // සංථ (x = anusvara)
    }

    @Test fun sanyakaGa_ngBeforeG() {
        assertEquals("\u0D9C\u0D9F", c("ganga")) // ගඟ
        assertEquals("\u0D85\u0D9F", c("anga")) // අඟ
    }

    @Test fun sanyakaDha_nd() {
        assertEquals("\u0D9A\u0DB3", c("kanda")) // කඳ
    }

    @Test fun sanyakaDa_Nd() {
        assertEquals("\u0D9A\u0DAC\u0DD2\u0DBA", c("kaNdiya")) // කඬිය (sanyaka da)
    }

    @Test fun sanyakaBa_mb() {
        assertEquals("\u0D85\u0DB9", c("amba")) // අඹ
        assertEquals("\u0D9A\u0DB9\u0DBA", c("kambaya")) // කඹය
    }

    @Test fun visarga_capitalH() {
        assertEquals("\u0DB6$VISARGA", c("baH")) // බඃ
    }

    @Test fun visarga_underscoreH() {
        assertEquals("\u0DB6$VISARGA", c("ba_h")) // බඃ
    }
}
