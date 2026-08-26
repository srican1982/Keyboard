package com.personal.sinhalakeyboard

/**
 * Helakuru-style Singlish → Sinhala transliteration engine.
 *
 * Ported from [@siyabasa/singlish](https://github.com/remeinium/singlish) (Apache-2.0).
 * Two-stage: greedy phoneme tokenization → contextual Unicode rendering.
 */
object SinglishConverter {

    private const val HAL = "\u0DCA"
    private const val ZWJ = "\u200D"

    private val singlishToPhoneme: List<Pair<String, String>> = listOf(
        "zdha" to "SANYAKA_DHA",
        "chh" to "ASPIRATED_CH",
        "thh" to "ASPIRATED_TH",
        "dhh" to "ASPIRATED_DH",
        "zga" to "SANYAKA_GA",
        "zja" to "SANYAKA_JA",
        "zda" to "SANYAKA_DA",
        "zqa" to "SANYAKA_DHA",
        "zka" to "SANYAKA_KA",
        "zha" to "SANYAKA_HA",
        "aa" to "V_AA",
        "Aa" to "V_AE_LONG",
        "AA" to "V_AE_LONG",
        "ai" to "V_AI",
        "au" to "V_AU",
        "ou" to "V_AU",
        "ii" to "V_II",
        "uu" to "V_UU",
        "ee" to "V_EE",
        "oo" to "V_OO",
        "Ru" to "V_RU_LONG",
        "Lu" to "SPECIAL_LU",
        "kh" to "KH",
        "gh" to "GH",
        "ch" to "CH",
        "ph" to "PH",
        "bh" to "BH",
        "th" to "TH",
        "dh" to "DH",
        "Sh" to "RETROFLEX_S",
        "sh" to "SH",
        "ng" to "N_G",
        "Th" to "RETROFLEX_TH",
        "Dh" to "RETROFLEX_DH",
        "Ba" to "SANYAKA_BA",
        "a" to "V_A",
        "A" to "V_AE",
        "i" to "V_I",
        "u" to "V_U",
        "U" to "V_UU",
        "e" to "V_E",
        "E" to "V_EE",
        "o" to "V_O",
        "O" to "V_OO",
        "R" to "V_RU",
        "k" to "K",
        "g" to "G",
        "j" to "J",
        "t" to "RETROFLEX_T",
        "d" to "RETROFLEX_D",
        "T" to "RETROFLEX_TH_SINGLE",
        "D" to "RETROFLEX_DH_SINGLE",
        "n" to "N",
        "N" to "RETROFLEX_N",
        "p" to "P",
        "b" to "B_LOWER",
        "B" to "SANYAKA_B",
        "m" to "M",
        "y" to "Y",
        "r" to "R_CONS",
        "l" to "L_CONS",
        "L" to "RETROFLEX_L",
        "v" to "V_CONS",
        "w" to "V_CONS",
        "s" to "S_CONS",
        "S" to "RETROFLEX_S",
        "h" to "H_CONS",
        "f" to "F",
        "q" to "DH",
        "x" to "ANUSVARA",
        "X" to "MAHAPRANAANUSVARA",
        "H" to "VISARGA",
        " " to " ",
        "\n" to "\n",
        "\t" to "\t",
        ":" to ":",
        ";" to ";",
        "." to ".",
        "-" to "-",
        "," to ",",
        "/" to "/",
        "?" to "?",
        "!" to "!",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "\"" to "\"",
        "'" to "'",
        "0" to "0", "1" to "1", "2" to "2", "3" to "3", "4" to "4",
        "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9",
    )

    private val consonantMap = mapOf(
        "K" to "\u0D9A", "KH" to "\u0D9B", "G" to "\u0D9C", "GH" to "\u0D9D",
        "CH" to "\u0DA0", "ASPIRATED_CH" to "\u0DA1", "J" to "\u0DA2",
        "RETROFLEX_T" to "\u0DA7", "RETROFLEX_TH" to "\u0DA8", "RETROFLEX_TH_SINGLE" to "\u0DA8",
        "RETROFLEX_D" to "\u0DA9", "RETROFLEX_DH" to "\u0DAA", "RETROFLEX_DH_SINGLE" to "\u0DAA",
        "RETROFLEX_N" to "\u0DAB", "TH" to "\u0DAD", "ASPIRATED_TH" to "\u0DAE",
        "DH" to "\u0DAF", "ASPIRATED_DH" to "\u0DB0", "N" to "\u0DB1",
        "P" to "\u0DB4", "PH" to "\u0DB5", "B_LOWER" to "\u0DB6", "BH" to "\u0DB7",
        "M" to "\u0DB8", "Y" to "\u0DBA", "R_CONS" to "\u0DBB", "L_CONS" to "\u0DBD",
        "RETROFLEX_L" to "\u0DC5", "V_CONS" to "\u0DC0", "SH" to "\u0DC1",
        "RETROFLEX_S" to "\u0DC2", "S_CONS" to "\u0DC3", "H_CONS" to "\u0DC4", "F" to "\u0DC6",
    )

    private val consonantSet = consonantMap.keys

    private val sanyakaMap = mapOf(
        "SANYAKA_GA" to "\u0D9F", "SANYAKA_JA" to "\u0DA6", "SANYAKA_DA" to "\u0DAC",
        "SANYAKA_DHA" to "\u0DB3", "SANYAKA_KA" to "\u0DA4", "SANYAKA_HA" to "\u0DA5",
        "SANYAKA_B" to "\u0DB9", "SANYAKA_BA" to "\u0DB9",
    )

    private val sanyakaSet = sanyakaMap.keys

    private val vowelStandalone = mapOf(
        "V_A" to "\u0D85", "V_AA" to "\u0D86", "V_AE" to "\u0D87", "V_AE_LONG" to "\u0D88",
        "V_I" to "\u0D89", "V_II" to "\u0D8A", "V_U" to "\u0D8B", "V_UU" to "\u0D8C",
        "V_RU" to "\u0D8D", "V_RU_LONG" to "\u0D8E", "V_E" to "\u0D91", "V_EE" to "\u0D92",
        "V_AI" to "\u0D93", "V_O" to "\u0D94", "V_OO" to "\u0D95", "V_AU" to "\u0D96",
    )

    private val vowelModifier = mapOf(
        "V_AA" to "\u0DCF", "V_AE" to "\u0DD0", "V_AE_LONG" to "\u0DD1",
        "V_I" to "\u0DD2", "V_II" to "\u0DD3", "V_U" to "\u0DD4", "V_UU" to "\u0DD6",
        "V_E" to "\u0DD9", "V_EE" to "\u0DDA", "V_AI" to "\u0DDB",
        "V_O" to "\u0DDC", "V_OO" to "\u0DDF", "V_AU" to "\u0DE0",
    )

    private val vowelSet = vowelStandalone.keys
    private val vowelModifierSet = vowelModifier.keys

    private val specialMap = mapOf(
        "ANUSVARA" to "\u0D82",
        "MAHAPRANAANUSVARA" to "\u0D9E",
        "VISARGA" to "\u0D83",
        "SPECIAL_LU" to "\u0DF4",
    )

    private val wordBoundary = setOf(
        " ", ".", ",", "!", "?", ";", ":", "\n", "\t", "-", "/",
        "(", ")", "[", "]", "\"", "'",
    )

    fun convert(text: String): String {
        if (text.isEmpty()) return text
        return phonemesToSinhala(singlishToPhonemes(text))
    }

    private fun singlishToPhonemes(text: String): List<String> {
        val phonemes = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            var matched = false
            for ((pattern, phoneme) in singlishToPhoneme) {
                if (i + pattern.length <= text.length && text.substring(i, i + pattern.length) == pattern) {
                    phonemes.add(phoneme)
                    i += pattern.length
                    matched = true
                    break
                }
            }
            if (!matched) {
                phonemes.add(text[i].toString())
                i++
            }
        }
        return phonemes
    }

    private fun phonemesToSinhala(phonemes: List<String>): String {
        val output = StringBuilder()
        var i = 0
        while (i < phonemes.size) {
            val current = phonemes[i]
            val next = phonemes.getOrNull(i + 1)

            specialMap[current]?.let {
                output.append(it)
                i++
                continue
            }

            if (current == "N_G") {
                val nChar = consonantMap.getValue("N")
                val gChar = consonantMap.getValue("G")
                when {
                    next != null && isVowelModifier(next) -> {
                        output.append(nChar).append(HAL).append(gChar).append(vowelModifier.getValue(next))
                        i += 2
                    }
                    next != null && isInherentA(next) -> {
                        output.append(nChar).append(HAL).append(gChar)
                        i += 2
                    }
                    next != null && isConsonant(next) -> {
                        output.append(nChar).append(HAL).append(gChar).append(HAL)
                        i++
                    }
                    else -> {
                        output.append(nChar).append(HAL).append(gChar).append(HAL)
                        i++
                    }
                }
                continue
            }

            if (isVowel(current)) {
                output.append(vowelStandalone.getValue(current))
                i++
                continue
            }

            if (isSanyaka(current)) {
                val sanyakaChar = sanyakaMap.getValue(current)
                when (current) {
                    "SANYAKA_B" -> {
                        when {
                            next != null && isVowelModifier(next) -> {
                                output.append(sanyakaChar).append(vowelModifier.getValue(next))
                                i += 2
                            }
                            next != null && isInherentA(next) -> {
                                output.append(sanyakaChar)
                                i += 2
                            }
                            next != null && (isConsonant(next) || isSanyaka(next)) -> {
                                output.append(sanyakaChar).append(HAL)
                                i++
                            }
                            else -> {
                                output.append(sanyakaChar).append(HAL)
                                i++
                            }
                        }
                    }
                    "SANYAKA_BA" -> {
                        output.append(sanyakaChar)
                        i++
                    }
                    else -> {
                        output.append(sanyakaChar)
                        i++
                    }
                }
                continue
            }

            if (isConsonant(current)) {
                val consonantChar = consonantMap.getValue(current)
                val afterConjunct = phonemes.getOrNull(i + 2)

                if (next != null && isConjunctable(next)) {
                    val conjunctChar = consonantMap.getValue(next)
                    when {
                        next == "R_CONS" && afterConjunct == "V_U" -> {
                            output.append(consonantChar).append("\u0DD8")
                            i += 3
                        }
                        next == "R_CONS" && afterConjunct == "V_UU" -> {
                            output.append(consonantChar).append("\u0DF2")
                            i += 3
                        }
                        afterConjunct != null && isVowelModifier(afterConjunct) -> {
                            output.append(consonantChar).append(HAL).append(ZWJ).append(conjunctChar)
                            output.append(vowelModifier.getValue(afterConjunct))
                            i += 3
                        }
                        afterConjunct != null && isInherentA(afterConjunct) -> {
                            output.append(consonantChar).append(HAL).append(ZWJ).append(conjunctChar)
                            i += 3
                        }
                        afterConjunct != null && (isConsonant(afterConjunct) || isSanyaka(afterConjunct) || afterConjunct == "N_G") -> {
                            output.append(consonantChar).append(HAL).append(ZWJ).append(conjunctChar).append(HAL)
                            i += 2
                        }
                        else -> {
                            output.append(consonantChar).append(HAL).append(ZWJ).append(conjunctChar).append(HAL)
                            i += 2
                        }
                    }
                    continue
                }

                when {
                    next != null && isVowelModifier(next) -> {
                        output.append(consonantChar).append(vowelModifier.getValue(next))
                        i += 2
                    }
                    next != null && isInherentA(next) -> {
                        output.append(consonantChar)
                        i += 2
                    }
                    next != null && (isConsonant(next) || isSanyaka(next) || next == "N_G") -> {
                        output.append(consonantChar).append(HAL)
                        i++
                    }
                    next == null || isWordBoundary(next) -> {
                        output.append(consonantChar).append(HAL)
                        i++
                    }
                    next != null && specialMap.containsKey(next) -> {
                        output.append(consonantChar)
                        i++
                    }
                    else -> {
                        output.append(consonantChar)
                        i++
                    }
                }
                continue
            }

            output.append(current)
            i++
        }
        return output.toString()
    }

    private fun isConsonant(token: String) = token in consonantSet
    private fun isSanyaka(token: String) = token in sanyakaSet
    private fun isVowel(token: String) = token in vowelSet
    private fun isInherentA(token: String) = token == "V_A"
    private fun isVowelModifier(token: String) = token in vowelModifierSet
    private fun isWordBoundary(token: String) = token in wordBoundary
    private fun isConjunctable(token: String) = token == "Y" || token == "R_CONS"
}
