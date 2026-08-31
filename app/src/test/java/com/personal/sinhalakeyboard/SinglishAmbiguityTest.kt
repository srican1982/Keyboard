package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglishAmbiguityTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    private val DA = "\u0DA9" // ඩ retroflex
    private val DHA = "\u0DAF" // ද dental
    private val KA = "\u0D9A"
    private val TA = "\u0DA7"
    private val ANUSVARA = "\u0D82"
    private val HAL = "\u0DCA"

    @Test
    fun daAmbiguity_dentalAndRetroflex() {
        assertEquals(DHA, c("da"))
        assertEquals(DA, c("dha"))
        assertTrue(SinglishAmbiguityVariants.liveVariants("da").contains("dha"))
    }

    @Test
    fun standaloneA_ambiguity() {
        assertEquals("\u0D85", c("a")) // අ
        assertEquals("\u0D86", c("aa")) // ආ
        assertEquals("\u0D87", c("ae")) // ඇ
        assertEquals("\u0D88", c("aee")) // ඈ
        val variants = SinglishAmbiguityVariants.liveVariants("a")
        assertTrue(variants.contains("aa"))
        assertTrue(variants.contains("ae"))
        assertTrue(variants.contains("aee"))
    }

    @Test
    fun kaAmbiguity_consonantVowelStem() {
        assertEquals(KA, c("ka")) // ක
        assertEquals("$KA\u0DCF", c("kaa")) // කා
        assertEquals("$KA\u0DD0", c("kae")) // කැ
        assertEquals("$KA\u0DD1", c("kaee")) // කෑ
        val variants = SinglishAmbiguityVariants.liveVariants("ka")
        assertTrue(variants.contains("kaa"))
        assertTrue(variants.contains("kae"))
        assertTrue(variants.contains("kaee"))
        assertTrue(variants.contains("k"))
    }

    @Test
    fun koAmbiguity_shortAndLongO() {
        assertEquals("$KA\u0DDC", c("ko")) // කො
        assertEquals("$KA\u0DDF", c("koo")) // කෝ
        assertTrue(SinglishAmbiguityVariants.liveVariants("ko").contains("koo"))
        assertTrue(SinglishAmbiguityVariants.liveVariants("koo").contains("ko"))
    }

    @Test
    fun keeAmbiguity_shortAndLongE() {
        assertEquals("$KA\u0DDA", c("kee")) // කී
        assertEquals("$KA\u0DD9", c("ke")) // කෙ
        assertTrue(SinglishAmbiguityVariants.liveVariants("kee").contains("ke"))
    }

    @Test
    fun kaaAmbiguity_alepillaAndAdaya() {
        assertEquals("$KA\u0DCF", c("kaa")) // කා
        assertEquals("$KA\u0DD0", c("kae")) // කැ
        assertEquals("$KA\u0DD1", c("kaee")) // කෑ
        val variants = SinglishAmbiguityVariants.liveVariants("kaa")
        assertTrue(variants.contains("kae"))
        assertTrue(variants.contains("kaee"))
    }

    @Test
    fun handaAmbiguity_sanyakaAndStacked() {
        val moon = c("handa") // හඳ (nd → ඳ)
        val junction = c("han dha")
        val spoon = c("haen dha")
        assertNotEquals(moon, junction)
        assertNotEquals(moon, spoon)
        assertNotEquals(junction, spoon)
        val variants = SinglishAmbiguityVariants.liveVariants("handa")
        assertTrue(variants.contains("han dha"))
        assertTrue(variants.contains("haen dha"))
        assertTrue(variants.contains("haenda"))
    }

    @Test
    fun tanAmbiguity_nAndAnusvara() {
        val tan = c("tan")
        val tang = c("tang")
        assertNotEquals(tan, tang)
        assertTrue(tang.contains(ANUSVARA))
        assertTrue(SinglishAmbiguityVariants.liveVariants("tan").contains("tang"))
        assertTrue(SinglishAmbiguityVariants.liveVariants("tang").contains("tan"))
    }

    @Test
    fun taAmbiguity_murdhajaAndDantaja() {
        assertEquals(TA, c("ta"))
        assertEquals("\u0DAD", c("tha"))
        assertTrue(SinglishAmbiguityVariants.liveVariants("ta").contains("tha"))
    }

    @Test
    fun nidida_mapsToCommonWord() {
        assertEquals("\u0DB1\u0DD2\u0DAF\u0DD2\u0DAF", c("nidida"))
    }
}
