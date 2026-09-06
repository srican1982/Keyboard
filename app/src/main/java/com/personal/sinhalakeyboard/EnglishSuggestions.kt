package com.personal.sinhalakeyboard

import android.content.Context
import android.provider.UserDictionary
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.Locale

/**
 * English-mode word suggestions — device spell checker + personal SQLite history only.
 */
class EnglishSuggestions(
    context: Context,
    private val personalHistory: PersonalHistoryDatabase? = null,
) {

    private val appContext = context.applicationContext
    private val textServicesManager =
        appContext.getSystemService(TextServicesManager::class.java)

    private var session: android.view.textservice.SpellCheckerSession? = null
    private var pendingPrefix: String = ""
    private var pendingCallback: ((List<SuggestionCandidate>) -> Unit)? = null
    private var pendingUserWords: List<SuggestionCandidate> = emptyList()

    init {
        openSession()
    }

    fun suggest(prefix: String, callback: (List<SuggestionCandidate>) -> Unit) {
        val p = prefix.trim()
        if (p.isEmpty()) {
            callback(emptyList())
            return
        }

        pendingPrefix = p
        pendingCallback = callback
        pendingUserWords = queryUserDictionary(p)

        val active = session
        if (active != null) {
            active.getSuggestions(TextInfo(p), 10)
        } else {
            deliverResults(p, emptyList())
        }
    }

    fun close() {
        session?.close()
        session = null
        pendingCallback = null
    }

    private fun openSession() {
        session?.close()
        session = textServicesManager?.newSpellCheckerSession(
            null,
            Locale.ENGLISH,
            object : android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener {
                override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                    val spellSuggestions = mutableListOf<SuggestionCandidate>()
                    val lower = pendingPrefix.lowercase()

                    results?.forEach { info ->
                        for (i in 0 until info.suggestionsCount) {
                            val word = info.getSuggestionAt(i) ?: continue
                            if (!EnglishSuggestionRanker.isEnglishOnly(word)) continue
                            if (word.lowercase().startsWith(lower) && word.lowercase() != lower) {
                                spellSuggestions.add(
                                    SuggestionCandidate(formatWord(word, pendingPrefix), word),
                                )
                            }
                        }
                    }

                    deliverResults(pendingPrefix, spellSuggestions)
                }

                override fun onGetSentenceSuggestions(
                    results: Array<out SentenceSuggestionsInfo>?,
                ) = Unit
            },
            true,
        )
    }

    private fun deliverResults(prefix: String, spellSuggestions: List<SuggestionCandidate>) {
        if (prefix != pendingPrefix) return
        val callback = pendingCallback ?: return
        pendingCallback = null

        val personalRows = personalHistory?.queryEnglishPrefix(prefix, limit = 12).orEmpty()
        val personalCandidates = personalRows.map { (word, _) ->
            SuggestionCandidate(formatWord(word, prefix), word, isPersonal = true)
        }

        val merged = linkedSetOf<SuggestionCandidate>()
        merged.addAll(personalCandidates)
        merged.addAll(pendingUserWords.filter { EnglishSuggestionRanker.isEnglishOnly(it.commitText) })
        merged.addAll(spellSuggestions)

        val personalCounts = personalHistory?.getCounts(
            merged.map { it.commitText },
            PersonalHistoryDatabase.MODE_ENGLISH,
        ).orEmpty()

        for ((word, count) in personalCounts) {
            if (count > 0 && merged.none { it.commitText.equals(word, ignoreCase = true) }) {
                merged.add(SuggestionCandidate(formatWord(word, prefix), word, isPersonal = true))
            }
        }

        callback(
            EnglishSuggestionRanker.rank(
                prefix = prefix,
                candidates = merged.toList(),
                personalCounts = personalCounts,
                limit = 8,
            ),
        )
    }

    private fun queryUserDictionary(prefix: String): List<SuggestionCandidate> {
        val lower = prefix.lowercase()
        val results = mutableListOf<SuggestionCandidate>()
        try {
            appContext.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                "${UserDictionary.Words.WORD} LIKE ?",
                arrayOf("$lower%"),
                "${UserDictionary.Words.WORD} ASC",
            )?.use { cursor ->
                val wordIdx = cursor.getColumnIndex(UserDictionary.Words.WORD)
                while (cursor.moveToNext() && results.size < 8) {
                    val word = cursor.getString(wordIdx) ?: continue
                    if (!EnglishSuggestionRanker.isEnglishOnly(word)) continue
                    if (word.lowercase() != lower) {
                        results.add(SuggestionCandidate(formatWord(word, prefix), word))
                    }
                }
            }
        } catch (_: Exception) {
            // User dictionary may be unavailable on some devices
        }
        return results
    }

    private fun formatWord(word: String, prefix: String): String {
        return word.replaceFirstChar { c ->
            if (prefix.firstOrNull()?.isUpperCase() == true) c.uppercaseChar() else c
        }
    }
}
