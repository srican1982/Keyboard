package com.personal.sinhalakeyboard

import android.content.Context

/** Local bigram next-word predictions (offline), ranked with personal history. */
class NextWordPredictor(
    context: Context,
    private val typingMemory: TypingMemory? = null,
    private val personalHistory: PersonalHistoryDatabase? = null,
) {

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
        val personal = typingMemory?.nextWordSuggestions(key, sinhala, limit).orEmpty()
        val seen = personal.map { it.commitText.lowercase() }.toMutableSet()
        val merged = personal.toMutableList()
        for (word in words) {
            if (sinhala && !containsSinhalaScript(word)) continue
            if (!sinhala && !EnglishSuggestionRanker.isEnglishOnly(word)) continue
            if (seen.add(word.lowercase())) {
                merged.add(
                    SuggestionCandidate(
                        display = word,
                        commitText = word,
                        isNextWord = true,
                    ),
                )
            }
            if (merged.size >= limit * 2) break
        }

        val mode = if (sinhala) PersonalHistoryDatabase.MODE_SINHALA else PersonalHistoryDatabase.MODE_ENGLISH
        val personalCounts = personalHistory?.getCounts(merged.map { it.commitText }, mode).orEmpty()

        return if (sinhala) {
            merged
                .filter { containsSinhalaScript(it.commitText) }
                .sortedWith(
                    compareByDescending<SuggestionCandidate> {
                        personalCounts[it.commitText] ?: 0
                    }.thenBy { it.commitText },
                )
                .take(limit)
        } else {
            EnglishSuggestionRanker.rank("", merged, personalCounts, limit)
        }
    }

    private fun containsSinhalaScript(text: String): Boolean =
        text.any { it.code in 0x0D80..0x0DFF }

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
