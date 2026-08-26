package com.personal.sinhalakeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Murdhaja / dantaja / taluju / mahaprana via Singlish case pairs. */
class SinglishHomophoneTest {

    private fun c(text: String) = SinglishConverter.convert(text)

    @Test fun thVsT_dantajaAndMurdhaja() {
        assertEquals("\u0DAD\u0DBB\u0DD4", c("tharu")) // තරු (th = ත)
        assertEquals("\u0DA7\u0DD2\u0DBA\u0DCF", c("Tiiyaa")) // ටීයා (T = ට)
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
        assertEquals("\u0DC3\u0DAD\u0DD2\u0DA7", c("sathuta")) // සතුට (s = ස)
        assertEquals("\u0DC1\u0DBB\u0DD2", c("shri")) // ශ්‍රී (sh = ශ)
        assertEquals("\u0DC0\u0DBB\u0DC2\u0DCF", c("varSaa")) // වර්ෂා (S = ෂ)
    }

    @Test fun mahaprana_capitalLetters() {
        assertEquals("\u0D9B\u0DD2\u0DBB", c("Kiira")) // ඛීර (K = ඛ)
        assertEquals("\u0DB5\u0DC3\u0DBA", c("Palaya")) // ඵලය (P = ඵ)
        assertEquals("\u0DB7\u0DCF\u0DC1\u0DCF\u0DC0", c("Baashaava")) // භාශාව (B = භ)
        assertEquals("\u0DA1\u0DCF\u0DBA\u0DCF", c("Chaayaa")) // ඡායා (Ch = ඡ)
        assertEquals("\u0DA3\u0DCF\u0DBA", c("Jhaana")) // ඣාන (J = ඣ)
    }

    @Test fun mahaprana_lowercaseDigraphsStillWork() {
        assertEquals("\u0D9B\u0DD2\u0DBB", c("khiira")) // kh = ඛ
        assertEquals("\u0DB7\u0DCF\u0DC1\u0DCF\u0DC0", c("bhaashaava")) // bh = භ
    }

    @Test fun sanyakaBa_viaBa() {
        assertEquals("\u0DB9", c("Ba")) // ඹ via Ba (capital B alone = භ)
    }
}
