package com.personal.sinhalakeyboard

/** English chips: personal_count * 10_000 + device rank (lower index = higher). */
object EnglishSuggestionRanker {

    fun rank(
        prefix: String,
        candidates: List<SuggestionCandidate>,
        personalCounts: Map<String, Int>,
        limit: Int = 12,
    ): List<SuggestionCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val key = prefix.trim().lowercase()
        val scored = candidates
            .filter {
                isEnglishOnly(it.commitText) ||
                    (it.isCloud && isEnglishCloudSuggestion(it.commitText))
            }
            .distinctBy { it.commitText.lowercase() }
            .mapIndexed { index, candidate ->
                val personal = personalCounts[candidate.commitText]
                    ?: personalCounts[candidate.commitText.lowercase()]
                    ?: 0
                val deviceRank = (candidates.size - index).coerceAtLeast(0)
                val score = PersonalHistoryDatabase.PERSONAL_WEIGHT * personal + deviceRank
                candidate to score
            }
            .sortedWith(
                compareByDescending<Pair<SuggestionCandidate, Int>> { it.second }
                    .thenBy { it.first.commitText.length }
                    .thenBy { it.first.commitText.lowercase() },
            )
            .take(limit)
            .map { (candidate, _) ->
                val personal = personalCounts[candidate.commitText]
                    ?: personalCounts[candidate.commitText.lowercase()]
                    ?: 0
                candidate.copy(isPersonal = personal > 0)
            }

        return if (scored.isEmpty() && key.isNotEmpty()) emptyList() else scored
    }

    fun isEnglishOnly(text: String): Boolean {
        if (text.isBlank() || text.contains(' ')) return false
        return isBasicLatinEnglish(text)
    }

    /** Cloud AI may return multi-word phrase completions. */
    fun isEnglishCloudSuggestion(text: String): Boolean {
        if (text.isBlank()) return false
        return isBasicLatinEnglish(text)
    }

    /** English chips must stay in basic Latin — never Sinhala script or other alphabets. */
    private fun isBasicLatinEnglish(text: String): Boolean {
        if (text.any { it.code in 0x0D80..0x0DFF }) return false

        var hasLetter = false
        for (ch in text) {
            when {
                ch in 'A'..'Z' || ch in 'a'..'z' -> hasLetter = true
                ch.isDigit() || ch.isWhitespace() -> continue
                ch in "'-.,!?\"()[]:;/" -> continue
                else -> return false
            }
        }

        return hasLetter
    }
}
