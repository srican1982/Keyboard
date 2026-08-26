package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Murdhaja / dantaja / taluju / mahaprana via Singlish case pairs. */
class SinglishHomophoneTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    private val HAL = "\u0DCA"
    private val ZWJ = "\u200D"
    private val RA = "\u0DBB"

    @Test fun thVsT_dantajaAndMurdhaja() {
        assertEquals("\u0DAD\u0DBB\u0DD4", c("tharu")) // තරු
        assertEquals("\u0DA7\u0DD3\u0DBA\u0DCF", c("Tiiyaa")) // ටීයා
    }

    @Test fun nVsN_dantajaAndMurdhaja() {
        assertEquals("\u0DB1\u0DB8", c("nama")) // නම
        assertEquals("\u0D9C\u0DD4\u0DAB", c("guNa")) // ගුණ
    }

    @Test fun lVsL_dantajaAndMurdhaja() {
        assertEquals("\u0DBD\u0DC3\u0DCA\u0DC3\u0DB1", c("lassana")) // ලස්සන
        assertEquals("\u0DB8\u0DC5", c("maLa")) // මළ
    }

    @Test fun sShVsSh_talujuAndMurdhaja() {
        assertEquals("\u0DC3\u0DAD\u0DD4\u0DA7", c("sathuta")) // සතුට
        assertEquals("\u0DC1$HAL$ZWJ$RA\u0DD2", c("shri")) // ශ්‍රි
        assertEquals("\u0DC0\u0DBB$HAL\u0DC2\u0DCF", c("varSaa")) // වර්ෂා
    }

    @Test fun mahaprana_K() {
        assertEquals("\u0D9B\u0DD3\u0DBB", c("Kiira")) // ඛීර
        assertEquals("\u0D9B\u0DD3\u0DBB", c("khiira")) // kh digraph
    }

    @Test fun mahaprana_P() {
        assertEquals("\u0DB5\u0DBD\u0DBA", c("Palaya")) // ඵලය
    }

    @Test fun mahaprana_B() {
        assertEquals("\u0DB7\u0DCF\u0DC1\u0DCF\u0DC0", c("Baashaava")) // භාශාව (Baa+shaava)
        assertEquals("\u0DB7\u0DCF\u0DC1\u0DCF\u0DC0", c("bhaashaava")) // bh digraph
        assertEquals("\u0DB9", c("Ba")) // ඹ sanyaka (exactly Ba)
    }

    @Test fun mahaprana_Ch() {
        assertEquals("\u0DA1\u0DCF\u0DBA\u0DCF", c("Chaayaa")) // ඡායා
    }

    @Test fun mahaprana_J() {
        assertEquals("\u0DA3\u0DCF\u0DB1", c("Jhaana")) // ඣාන (Jhaa+na)
        assertEquals("\u0DA3\u0DCF\u0DB1", c("Jaana")) // ඣාන (Jaa+na)
    }
}
