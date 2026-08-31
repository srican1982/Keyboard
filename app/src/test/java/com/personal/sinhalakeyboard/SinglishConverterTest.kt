package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies Sinhala Pillam (vowel signs) attach correctly to consonants. */
class SinglishConverterTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    // Base consonant: ක (ka)
    private val KA = "\u0D9A"

    @Test fun alepilla_longA() {
        assertEquals("$KA\u0DCF", c("kaa")) // කා
    }

    @Test fun ispilla_shortI() {
        assertEquals("$KA\u0DD2", c("ki")) // කි
    }

    @Test fun ispilla_longI() {
        assertEquals("$KA\u0DD3", c("kii")) // කී
    }

    @Test fun kombuwa_shortE() {
        assertEquals("$KA\u0DD9", c("ke")) // කෙ
    }

    @Test fun kombuwa_longE() {
        assertEquals("$KA\u0DDA", c("kee")) // කේ
    }

    @Test fun halKeerima_wordEnd() {
        assertEquals("$KA\u0DCA", c("k")) // ක්
    }

    @Test fun halKeerima_betweenConsonants() {
        assertEquals("$KA\u0DCA\u0DA7\u0DCA", c("kt")) // ක්ට්
        assertEquals("$KA\u0DCA\u0DA7", c("kta")) // ක්ට
    }

    @Test fun paapilla_shortU() {
        assertEquals("$KA\u0DD4", c("ku")) // කු
    }

    @Test fun paapilla_longU() {
        assertEquals("$KA\u0DD6", c("kuu")) // කූ
        assertEquals("$KA\u0DD6", c("kU")) // කූ
    }

    @Test fun adaya_shortAe() {
        assertEquals("$KA\u0DD0", c("kae")) // කැ
        assertEquals("$KA\u0DD0", c("kA")) // කැ
    }

    @Test fun digaAdaya_longAe() {
        assertEquals("$KA\u0DD1", c("kaee")) // කෑ
        assertEquals("$KA\u0DD1", c("kAA")) // කෑ
        assertEquals("$KA\u0DD1", c("kAa")) // කෑ
    }

    @Test fun standaloneVowels() {
        assertEquals("\u0D85", c("a")) // අ
        assertEquals("\u0D86", c("aa")) // ආ
        assertEquals("\u0D87", c("ae")) // ඇ
        assertEquals("\u0D88", c("aee")) // ඈ
        assertEquals("\u0D89", c("i")) // ඉ
        assertEquals("\u0D91", c("e")) // එ
    }

    @Test fun inherentVowelA() {
        assertEquals(KA, c("ka")) // ක (inherent "a", no visible pillam)
    }

    @Test fun gaettaPilla_ru() {
        assertEquals("$KA\u0DD8", c("kR")) // කෘ
        assertEquals("$KA\u0DF2", c("kRu")) // කෲ
        assertEquals("$KA\u0DD8", c("kru")) // කෘ via r+u cluster
    }

    @Test fun ndCluster_sanyakaDha() {
        assertEquals("\u0DB3\u0DCA", c("nd")) // ඳ්
    }

    @Test fun hondayi_fullWord() {
        assertEquals("\u0DC4\u0DDC\u0DB3\u0DBA\u0DD2", c("hondayi")) // හොඳයි
    }

    @Test fun otherVowelSigns() {
        assertEquals("$KA\u0DDC", c("ko")) // කො — kombuwa + alepilla
        assertEquals("$KA\u0DDF", c("koo")) // කෝ — kombuwa + hal
        assertEquals("$KA\u0DDB", c("kai")) // කෛ
    }

    private val HAL = "\u0DCA"
    private val ZWJ = "\u200D"
    private val RA = "\u0DBB"
    private val YA = "\u0DBA"

    @Test fun yansaya_conjunctY() {
        assertEquals("\u0DC0$HAL$ZWJ$YA", c("vya")) // ව්‍ය
        assertEquals("\u0DAF$HAL$ZWJ$YA", c("dhya")) // ධ්‍ය
        assertEquals("\u0DA7$HAL$ZWJ$YA", c("tya")) // ට්‍ය
    }

    @Test fun rakansaya_conjunctRWithVowel() {
        assertEquals("$KA$HAL$ZWJ$RA", c("kra")) // ක්‍ර
        assertEquals("\u0DB4$HAL$ZWJ$RA", c("pra")) // ප්‍ර
        assertEquals("\u0DC1$HAL$ZWJ$RA", c("shra")) // ශ්‍ර
    }

    @Test fun repaya_rBeforeConsonant() {
        assertEquals("$KA$RA$HAL$ZWJ\u0DB8", c("krma")) // කර්‍ම
        assertEquals("\u0DAF$RA$HAL$ZWJ\u0DB8", c("dhrma")) // ධර්‍ම
        assertEquals("\u0DC0$RA$HAL$ZWJ\u0DC1", c("vrsha")) // වර්‍ශ
    }

    @Test fun rakansayaAndRepaya_distinct() {
        // Vowel after r → rakansaya (krama)
        assertEquals("$KA$HAL$ZWJ$RA\u0DB8", c("krama")) // ක්‍රම
        // Consonant after r with no vowel → repaya (karma)
        assertEquals("$KA$RA$HAL$ZWJ\u0DB8", c("krma")) // කර්‍ම
    }

    @Test fun prashna_usesRakansaya() {
        // pra + sh + na — r followed by vowel, not repaya
        assertEquals(
            "\u0DB4$HAL$ZWJ$RA\u0DC1$HAL\u0DB1",
            c("prashna"),
        ) // ප්‍රශ්න
    }

    @Test fun sambhawithawa_samPlusBhaNotSanyakaBa() {
        // සම්භාවිතාව — mb before bh is m+භ, not ඹ; lazy a→aa in bha+wi and tha+wa
        assertEquals(
            "\u0DC3\u0DB8\u0DCA\u0DB7\u0DCF\u0DC0\u0DD2\u0DAD\u0DCF\u0DC0",
            c("sambhawithawa"),
        )
    }

    @Test fun kambaya_keepsMbAsSanyakaBa() {
        assertEquals("\u0D9A\u0DB9", c("kamb")) // කඹ
    }
}
