package com.personal.sinhalakeyboard

import android.content.Context

/** Local bigram next-word predictions (offline). */
class NextWordPredictor(context: Context) {

    private val englishProfessional: Map<String, List<String>>
    private val englishFriendly: Map<String, List<String>>
    private val sinhalaBigrams: Map<String, List<String>>

    init {
        englishProfessional = loadBigrams(context, "next_words_en_professional.txt")
        englishFriendly = loadBigrams(context, "next_words_en_friendly.txt")
        sinhalaBigrams = loadBigrams(context, "next_words_si.txt")
    }

    fun predict(
        lastWord: String,
        sinhala: Boolean,
        tone: EnglishTone = EnglishTone.PROFESSIONAL,
        limit: Int = 6,
    ): List<SuggestionCandidate> {
        if (lastWord.isEmpty()) return emptyList()
        val key = if (sinhala) lastWord.trim() else lastWord.lowercase()
        val map = when {
            sinhala -> sinhalaBigrams
            tone == EnglishTone.FRIENDLY -> englishFriendly
            else -> englishProfessional
        }
        val words = map[key].orEmpty()
        return words.take(limit).map { word ->
            SuggestionCandidate(
                display = word,
                commitText = word,
                isNextWord = true,
            )
        }
    }

    private fun loadBigrams(context: Context, assetName: String): Map<String, List<String>> {
        return try {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                    val parts = trimmed.split("|", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val key = parts[0].trim().lowercase()
                    val next = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (key.isEmpty() || next.isEmpty()) null else key to next
                }.toMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
